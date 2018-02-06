#include "../cande_commons.h"
#include "cande_debug.c"
#include "sar_parser.h"

#include <stdio.h>
#include <stdlib.h>
#include <signal.h>
#include <unistd.h>
#include <sched.h>
#include <string.h>
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
#define SCHED_PRIORITY 50

/* light-detector thresholds */
#define THRESHOLD_SYS 50
#define THRESHOLD_USER 4
#define THRESHOLD_IOWAIT 50
#define THRESHOLD_RUN 4
#define THRESHOLD_CS 25
#define THRESHOLD_MEMFREE 30720 /* kb = 30mb */

#define EC_CPU_MIN_COUNT 2
#define EC_MEM_MIN_COUNT 2

struct cande_msg {
    int sd;
    struct sockaddr_nl d_nladdr;
    struct nlmsghdr *nlh;
    struct iovec iov;
    struct msghdr msg;
};

#define CANDE_MSG_DATA(msg) NLMSG_DATA((msg)->nlh)
#define CANDE_MSG_SET_LEN(msg, len) \
	(void)((msg)->nlh->nlmsg_len = (msg)->iov.iov_len = NLMSG_LENGTH(len))

static int init_communication(struct cande_msg *msg,
                              struct detection_metrics *metrics);
static void send_message(struct cande_msg *msg);
static void start_light_detector(struct detection_metrics *metrics,
                                 struct cande_msg *msg);
static void run_light_detector(FILE *sar, struct detection_metrics *metrics,
                               struct cande_msg *msg);
static void stop_light_detector(struct detection_metrics *metrics,
                                struct cande_msg *msg);
static void check_and_signal_error(int *ec_cpu_count, int *ec_mem_count,
                                   struct detection_metrics *metrics,
                                   struct cande_msg *msg);
static void signal_error(int error_code, struct detection_metrics *metrics,
                         struct cande_msg *msg);

static int stop = 0;

int main()
{
    struct detection_metrics metrics;
    struct cande_msg msg;

    /* ensure well defined shutdown when initialization fails */
    metrics.cpu = NULL;
    msg.sd = -1;
    msg.nlh = NULL;

    start_light_detector(&metrics, &msg);
    stop_light_detector(&metrics, &msg);

    return stop ? EXIT_SUCCESS : EXIT_FAILURE;
}

static int init_communication(struct cande_msg *msg,
                              struct detection_metrics *metrics)
{
    struct sockaddr_nl s_nladdr;
    /* determine maximal size needed for sending metrics dependent
     * on cpu count */
    /* see "communication_specification.pdf" */
    size_t max_msg_len;
    if (metrics->num_cpus > 6)
        max_msg_len = 2 + metrics->num_cpus * 2;
    else
        max_msg_len = 8 + metrics->num_cpus;

    /* set generic netlink-header fields */
    msg->nlh = calloc(1, NLMSG_SPACE(max_msg_len));
    if (!msg->nlh)
        return 0;
    msg->nlh->nlmsg_pid = getpid();
    msg->nlh->nlmsg_flags = 1;
    msg->nlh->nlmsg_type = 0;

    msg->sd = socket(AF_NETLINK, SOCK_RAW, NETLINK_NITRO);
    if (msg->sd < 0)
        return 0;

    /* source address */
    memset(&s_nladdr, 0, sizeof(s_nladdr));
    s_nladdr.nl_family = AF_NETLINK;
    s_nladdr.nl_pad = 0;
    s_nladdr.nl_pid = getpid();
    bind(msg->sd, (struct sockaddr *)&s_nladdr, sizeof(s_nladdr));

    /* destination address */
    memset(&msg->d_nladdr, 0, sizeof(msg->d_nladdr));
    msg->d_nladdr.nl_family = AF_NETLINK;
    msg->d_nladdr.nl_pad = 0;
    msg->d_nladdr.nl_pid = 0; /* destined to kernel */

    /* set generic iov fields */
    msg->iov.iov_base = msg->nlh;

    /* set generic message fields */
    memset(&msg->msg, 0, sizeof(msg->msg));
    msg->msg.msg_name = &msg->d_nladdr;
    msg->msg.msg_namelen = sizeof(msg->d_nladdr);
    msg->msg.msg_iov = &msg->iov;
    msg->msg.msg_iovlen = 1;

    return 1;
}

static void send_message(struct cande_msg *msg)
{
    if (sendmsg(msg->sd, &msg->msg, 0) < 0)
        perror("Sending message failed");
}

static void sig_handler(int signo)
{
    (void)signo;
    stop = 1;
}

static void start_light_detector(struct detection_metrics *metrics,
                                 struct cande_msg *msg)
{
    struct sched_param param;
    FILE *fp;

    /* set process scheduling priority to realtime */
    param.sched_priority = SCHED_PRIORITY;
    if (sched_setscheduler(0, SCHED_RR, &param)) {
        perror("Setting process to realtime priority failed");
    }

    /* register signal-handler */
    if (signal(SIGINT, sig_handler) == SIG_ERR
            || signal(SIGTERM, sig_handler) == SIG_ERR) {
        perror("Registering of the signal-hander failed");
    }

    metrics->num_cpus = sysconf(_SC_NPROCESSORS_ONLN);
    metrics->cpu = malloc(sizeof(*metrics->cpu) * metrics->num_cpus);
    if (!metrics->cpu) {
        perror("Memory allocation failed");
        return;
    }

    if (!init_communication(msg, metrics)) {
        perror("Connecting to heavy-detector failed");
        return;
    }

    /* start child process running the sar command */
    fp = popen(SAR_COMMAND, "r");
    if (fp == NULL) {
        perror("Popen failed creating or executing the sar process");
        return;
    }

    if (!init_parser(fp))
        printf("Parser initialization failed.\n");
    else
        run_light_detector(fp, metrics, msg);

    fclose(fp);
}

static void run_light_detector(FILE *sar, struct detection_metrics *metrics,
                               struct cande_msg *msg)
{
    int ec_cpu_count = 0;
    int ec_mem_count = 0;

    /* signal heavy-detector that the detection process has started */
    signal_error(EC_HELLO_SERVER, metrics, msg);

    /* loop getting system hang metrics from the sar process */
    while (parse_metrics(sar, metrics)) {
        print_metrics(metrics);
#ifdef CANDE_DEBUG
        printf("ec_cpu_count: %d, ec_mem_count: %d\n",
               ec_cpu_count, ec_mem_count);
#endif
        check_and_signal_error(&ec_cpu_count, &ec_mem_count, metrics,
                               msg);

        if (stop)
            return;
    }

    printf("Parser failed.\n");
}

static void stop_light_detector(struct detection_metrics *metrics,
                                struct cande_msg *msg)
{
    free(metrics->cpu);

    close(msg->sd);

    free(msg->nlh);
}

static void check_and_signal_error(int *ec_cpu_count, int *ec_mem_count,
                                   struct detection_metrics *metrics,
                                   struct cande_msg *msg)
{
    unsigned int i, isCPUErr = 0, signaled = 0;

    /* check if metrics exceed the specified thresholds */
    for (i = 0; i < metrics->num_cpus; i++) {
        struct cpu_metrics *cpu = &metrics->cpu[i];
        if (cpu->sys > THRESHOLD_SYS && cpu->usr < THRESHOLD_USER) {
            if (*ec_cpu_count >= EC_CPU_MIN_COUNT) {
                signaled = 1;
                signal_error(EC_CPU_ERROR, metrics, msg);
            } else {
                isCPUErr = 1;
            }
        }

        if (signaled != 1 && cpu->iowait > THRESHOLD_IOWAIT) {
            signaled = 1;
            signal_error(EC_CPU_ERROR, metrics, msg);
        }
    }

    if (isCPUErr)
        (*ec_cpu_count)++;
    else
        *ec_cpu_count = 0;

    if (metrics->memfree < THRESHOLD_MEMFREE) {
        if (*ec_mem_count >= EC_MEM_MIN_COUNT) {
            signaled = 1;
            signal_error(EC_MEM_ERROR, metrics, msg);
        } else {
            (*ec_mem_count)++;
        }
    } else {
        *ec_mem_count = 0;
    }

    if (metrics->run > THRESHOLD_RUN * metrics->num_cpus
            || metrics->cs < THRESHOLD_CS * metrics->num_cpus) {
        signaled = 1;
        signal_error(EC_PROC_ERROR, metrics, msg);
    }

    if (!signaled)
        signal_error(EC_NONE, metrics, msg);
}

void signal_error(int error_code, struct detection_metrics *metrics,
                  struct cande_msg *msg)
{
    /* to understand the structure of the sent packages,
     * look up "communication_specification.pdf" in the documentation */
    unsigned int i;
    unsigned char *data_it = CANDE_MSG_DATA(msg);
    *data_it++ = (unsigned char)error_code;

    switch (error_code) {
    case EC_HELLO_SERVER:
        *data_it++ = (unsigned char)metrics->num_cpus;
        break;
    case EC_NONE:
        break;
    case EC_CPU_ERROR:
        for (i = 0; i < metrics->num_cpus; i++) {
            *data_it++ = (unsigned char)metrics->cpu[i].sys;
            *data_it++ = (unsigned char)metrics->cpu[i].usr;
        }
        break;
    case EC_MEM_ERROR: {
        struct memerr_data {
            unsigned blk:8;
            unsigned pswpout:16;
            unsigned memfree:24;
        } __attribute__((packed)) *metric_data;

        for (i = 0; i < metrics->num_cpus; i++) {
            *data_it++ = (unsigned char)metrics->cpu[i].iowait;
        }

        metric_data = (struct memerr_data *)data_it;
        data_it += sizeof(*metric_data);

        metric_data->blk = metrics->blk;
        metric_data->pswpout = metrics->pswpout;
        metric_data->memfree = metrics->memfree;

        break;
    }
    case EC_PROC_ERROR:
        *data_it++ = (unsigned char)metrics->run;
        *data_it++ = (unsigned char)metrics->blk;
        break;
    }
    CANDE_MSG_SET_LEN(msg, data_it - (unsigned char*)CANDE_MSG_DATA(msg));
    send_message(msg);
#ifdef CANDE_DEBUG
    printf("signaled error with code: %d\n", error_code);
#endif
}
