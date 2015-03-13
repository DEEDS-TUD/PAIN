#include "../cande_commons.h"
#include "cande_debug.c"
#include <stdio.h>
#include <stdlib.h> // malloc, free
#include <signal.h> // signal
#include <unistd.h> // sysconf
#include <sched.h>  // sched_setscheduler
#include "sar_parser.h"
#include <string.h>
// communication
#include <sys/types.h>
#include <sys/socket.h>
#include <linux/netlink.h>

/*
 * The sar program with appropriate flags to deliver hang detection metrics
 * flags:
 * -u cpu statistics
 * -W swapping statistics
 * -r memory statistics
 * -w context switchtes per second
 * -d percentage of cpu time during which I/O requests were issued to the device
 * -q task statistics
 */
#define SAR_COMMAND "sar -uWrwq -P ALL 1"
#define BUFSIZE 100
#define SCHED_PRIORITY 50

// light-detector thresholds
#define THRESHOLD_SYS 50
#define THRESHOLD_USER 4
#define THRESHOLD_IOWAIT 70
#define THRESHOLD_RUN 8
#define THRESHOLD_CS 15
#define THRESHOLD_MEMFREE 30720 //kb = 30mb 

#define EC_CPU_MIN_COUNT 2
#define EC_MEM_MIN_COUNT 2

#define MAX(a,b) (((a)>(b))?(a):(b))

typedef void sigfunc(int);

int start_light_detector(void);
void stop_light_detector(void);
sigfunc *signal(int, sigfunc*);
int init_communication(void);
void check_and_signal_error(void);
int signal_error(int);
void send_message(void);

static int ec_cpu_count;
static int ec_mem_count;

// sar
static struct detection_metrics *metrics;
static FILE *fp;
static char sar_buf[BUFSIZE];

// communication
static struct sockaddr_nl s_nladdr, d_nladdr;
static struct msghdr msg;
static struct nlmsghdr *nlh = NULL;
static struct iovec iov;
static int sd = -1;
static unsigned char * data;
static size_t data_len;

int main(int argc, const char* argv[]) {
	// this function should never return
	return start_light_detector();
}

void sig_handler(int signo) {
	// this is the correct termination way for the light detector
	stop_light_detector();
	exit(EXIT_SUCCESS);
}

int start_light_detector(void) {
	int error_code = EC_NONE;

	// set process scheduling priority to realtime
	struct sched_param param;
	param.sched_priority = SCHED_PRIORITY;
	if (sched_setscheduler(0 /*this process*/, SCHED_RR /*round robin*/,
			&param)) {
		perror("Setting process to realtime priority failed");
		return -1;
	}

	// register signal-handler
	if (signal(SIGINT, sig_handler) == SIG_ERR
			|| signal(SIGTERM, sig_handler) == SIG_ERR) {
		perror("Registering of the signal-hander failed");
		return -1;
	}

	metrics = malloc(sizeof(struct detection_metrics));
	if (!metrics) {
		perror("Allocation memory failed");
		return -1;
	}
	metrics->num_cpus = sysconf( _SC_NPROCESSORS_ONLN);
	metrics->cpu = malloc(sizeof(struct cpu_metrics) * metrics->num_cpus);
	if (!metrics->cpu) {
		perror("Allocation memory failed");
		return -1;
	}

	if (init_communication() < 0) {
		perror("Connecting to heavy-detector failed");
		return -1;
	}

	// start child process running the sar command
	fp = popen(SAR_COMMAND, "r");
	if (fp == NULL) {
		perror("Popen failed creating or executing the sar process");
		return -1;
	}

	// signal heavy-detector that the detection process has started
	signal_error(EC_HELLO_SERVER);
        ec_cpu_count = 0;
        ec_mem_count = 0;

	// infinite loop getting system hang metrics from the sar process
	while (fgets(sar_buf, BUFSIZE, fp) != NULL) {
		if (parse_metrics(sar_buf, metrics) == STATE_FINISH) {
			print_metrics(metrics);
#ifdef CANDE_DEBUG
			printf("ec_cpu_count: %d, ec_mem_count: %d\n",ec_cpu_count,ec_mem_count);
#endif
			check_and_signal_error();
		}
	}

	// this function should never return
	return -1;
}

void stop_light_detector(void) {
	pclose(fp);
	free(metrics);
	if (sd >= 0)
		close(sd);
}

void check_and_signal_error() {
	int i,isCPUErr = 0,signaled = 0;

	// check if metrics exceed the specified thresholds
	for (i = 0; i < metrics->num_cpus; i++) {
		if (metrics->cpu[i].sys > THRESHOLD_SYS
				&& metrics->cpu[i].usr < THRESHOLD_USER){
			if(ec_cpu_count >= EC_CPU_MIN_COUNT){
                                signaled = 1;
                                signal_error(EC_CPU_ERROR);
                                }
                        else
                                isCPUErr = 1;
                }

               if(signaled != 1 && metrics->cpu[i].iowait > THRESHOLD_IOWAIT){
                        signaled = 1;                        
                        signal_error(EC_CPU_ERROR);
                }
	}

        if(isCPUErr)
                ec_cpu_count++;
        else
                ec_cpu_count = 0;

	if (metrics->memfree < THRESHOLD_MEMFREE){
                if(ec_mem_count >= EC_MEM_MIN_COUNT){
                        signaled = 1;		
                        signal_error(EC_MEM_ERROR);
                }
                else 
                        ec_mem_count++;
        }else
                ec_mem_count = 0;

	if (metrics->run > THRESHOLD_RUN * metrics-> num_cpus || metrics->cs < THRESHOLD_CS * metrics->num_cpus){
                signaled = 1;
		signal_error(EC_PROC_ERROR);
        }
        
        if(signaled == 0)	
                signal_error(EC_NONE);
}

int signal_error(int error_code) {
	// to understand the structure of the sent packages, look up "communication_specification.pdf" in the documentation
	int i;
	data[0] = (char) error_code;

	switch (error_code) {
	case EC_HELLO_SERVER:
		data[1] = (char) metrics->num_cpus;
		data_len = 2;
		break;
	case EC_NONE:
		data_len = 1;
		break;
	case EC_CPU_ERROR:
		for (i = 0; i < metrics->num_cpus; i++) {
			data[1 + i * 2] = (char) metrics->cpu[i].sys;
			data[2 + i * 2] = (char) metrics->cpu[i].usr;
		}
		data_len = 1 + metrics->num_cpus * 2;
		break;
	case EC_MEM_ERROR: {
		for (i = 0; i < metrics->num_cpus; i++) {
			data[1 + i] = (char) metrics->cpu[i].iowait;
		}
		data[1 + metrics->num_cpus] = (char) metrics->blk;
		data[2 + metrics->num_cpus] = (char) ((metrics->pswpout & 0x0000ff00) >> 8);
		data[3 + metrics->num_cpus] = (char) (metrics->pswpout & 0x000000ff);
		data[4 + metrics->num_cpus] = (char) ((metrics->memfree & 0x00ff0000) >> 16);
		data[5 + metrics->num_cpus] = (char) ((metrics->memfree & 0x0000ff00 ) >> 8);
		data[6 + metrics->num_cpus] = (char) (metrics->memfree & 0x000000ff);
		data_len = 8 + metrics->num_cpus;
	}
		break;
	case EC_PROC_ERROR: {
		data[1] = (char) metrics->run;
		data[2] = (char) metrics->blk;
		data_len = 3;
	}
	}
	send_message();
#ifdef CANDE_DEBUG
	printf("signaled error with code: %d\n", error_code);
#endif

	return 0;
}

int init_communication() {
	sd = socket(AF_NETLINK, SOCK_RAW, NETLINK_NITRO);
	if (sd < 0)
		return -1;

	// source address
	memset(&s_nladdr, 0, sizeof(s_nladdr));
	s_nladdr.nl_family = AF_NETLINK;
	s_nladdr.nl_pad = 0;
	s_nladdr.nl_pid = getpid();
	bind(sd, (struct sockaddr*) &s_nladdr, sizeof(s_nladdr));

	// destination address
	memset(&d_nladdr, 0, sizeof(d_nladdr));
	d_nladdr.nl_family = AF_NETLINK;
	d_nladdr.nl_pad = 0;
	d_nladdr.nl_pid = 0; /* destined to kernel */

	// set generic netlink-header fields
	nlh = (struct nlmsghdr *) malloc(sizeof(struct nlmsghdr));
	memset(nlh, 0, sizeof(struct nlmsghdr));
	nlh->nlmsg_pid = getpid();
	nlh->nlmsg_flags = 1;
	nlh->nlmsg_type = 0;

	// set generic iov fields
	iov.iov_base = (void *) nlh;

	// set generic message fields
	memset(&msg, 0, sizeof(msg));
	msg.msg_name = (void *) &d_nladdr;
	msg.msg_namelen = sizeof(d_nladdr);
	msg.msg_iov = &iov;
	msg.msg_iovlen = 1;

	// determine maximal size needed for sending metrics dependent on cpu count
	// see "communication_specification.pdf"
	data = malloc(
			sizeof(char) * MAX(8+metrics->num_cpus,2+metrics->num_cpus*2));

	return 0;
}

void send_message() {
	// set header data
	nlh->nlmsg_len = NLMSG_LENGTH(data_len);
	iov.iov_len = nlh->nlmsg_len;
	// set payload
	memcpy(NLMSG_DATA(nlh), data, data_len);

	if(sendmsg(sd, &msg, 0)< 0)
		perror("sending message failed");
}
