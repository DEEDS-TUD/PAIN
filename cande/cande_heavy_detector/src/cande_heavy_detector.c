#include "../../cande_commons.h"
#include "taskutil.h"

#include <linux/init.h>
#include <linux/module.h>
#include <linux/kernel.h>
#include <linux/kthread.h>
#include <linux/sched.h>
#include <linux/time.h>
#include <linux/timer.h>
#include <linux/slab.h>
#include <linux/string.h>
/* communication */
#include <linux/netlink.h>
#include <net/sock.h>
#include <net/net_namespace.h>
/* timer */
#include <linux/timer.h>

MODULE_LICENSE("GPL");

#define TIMEOUT 5000
/* heavy-detector thresholds */
#define THRESHOLD_SYS 95
#define THRESHOLD_USR 1
#define THRESHOLD_IOWAIT 90
#define THRESHOLD_RUN 10
#define THRESHOLD_BLK 10
#define THRESHOLD_MEMFREE 20480 /* kb = 20mb */
#define THRESHOLD_PSWPOUT INT_MIN
/* #define THRESHOLD_UTIL 90 */

static int start_heavy_detector(void);
static void stop_heavy_detector(void);
static void receive_msg(struct sk_buff *);
static void process_msg(void);
static int process_data(void);
static void signal_hang(unsigned long);
static void set_timer(void);

static struct detection_metrics metrics;
static int ec;
static struct timer_list timer;
/* communication */
static struct sock *nl_sk = NULL;
static unsigned char *data;

static int start_heavy_detector(void)
{
    nl_sk = netlink_kernel_create(&init_net, NETLINK_NITRO, 0, receive_msg,
                                  NULL, THIS_MODULE);
    if (nl_sk == NULL) {
        printk(KERN_ALERT "cande: starting server failed\n");
        return -1;
    }

    printk(KERN_INFO "cande: cande_heavy_detector loaded.\n");
    return 0;
}

static void stop_heavy_detector(void)
{
    del_timer(&timer);
    sock_release(nl_sk->sk_socket);
    kfree(metrics.cpu);
    printk(KERN_INFO "cande: cande_heavy_detector unloaded.\n");
}

static void receive_msg(struct sk_buff *skb)
{
    struct nlmsghdr *nlh = NULL;
    if (skb == NULL) {
        printk(KERN_DEBUG "cande: skb is NULL \n");
        return;
    }
    nlh = (struct nlmsghdr *)skb->data;
    data = (char *)NLMSG_DATA(nlh);

    process_msg();
    if (process_data() == 1) {
        signal_hang(0);
    }

    ec = EC_NONE;
}

static void process_msg(void)
{
    /* to understand the structure of the sent packages, look up */
    /* "communication_specification.pdf" in the documentation */
    int i;
    unsigned char *data_it = data;
    ec = *data_it++;

    switch (ec) {
    case EC_HELLO_SERVER:
        metrics.num_cpus = *data_it++;
        metrics.cpu = kmalloc(sizeof(*metrics.cpu) * metrics.num_cpus,
                              GFP_KERNEL);
        setup_timer(&timer, signal_hang, 0);
        break;
    case EC_NONE:
        break;
    case EC_CPU_ERROR:
        for (i = 0; i < metrics.num_cpus; i++) {
            metrics.cpu[i].sys = *data_it++;
            metrics.cpu[i].usr = *data_it++;
        }
        break;
    case EC_MEM_ERROR: {
        struct metric_data {
            unsigned blk:8;
            unsigned pswpout:16;
            unsigned memfree:24;
        } __attribute__((packed)) *metric_data;
        for (i = 0; i < metrics.num_cpus; i++) {
            metrics.cpu[i].iowait = *data_it++;
        }
        metric_data = (struct metric_data *)data_it;
        metrics.blk = metric_data->blk;
        metrics.pswpout = metric_data->pswpout;
        metrics.memfree = metric_data->memfree;
        break;
    }
    case EC_PROC_ERROR:
        metrics.run = data[1];
        metrics.blk = data[2];
    }

    set_timer();
}

/* returns 1 if a hang is assumed */
static int process_data()
{
    int i;
    switch (ec) {
    case EC_CPU_ERROR:
        for (i = 0; i < metrics.num_cpus; i++) {
            if (metrics.cpu[i].usr <= THRESHOLD_USR
                    && metrics.cpu[i].sys >= THRESHOLD_SYS)
                return 1;
        }
        break;
    case EC_MEM_ERROR:
        if (metrics.pswpout >= THRESHOLD_PSWPOUT
                && metrics.memfree <= THRESHOLD_MEMFREE) {
            for (i = 0; i < metrics.num_cpus; i++) {
                if (metrics.cpu[i].iowait >= THRESHOLD_IOWAIT)
                    return 1;
            }
        }
        if (metrics.blk >= THRESHOLD_BLK
                && check_abnormal_memory_consumption())
            return 1;
        break;
    case EC_PROC_ERROR:
        if (metrics.run >= THRESHOLD_RUN * metrics.num_cpus
                && metrics.blk >= THRESHOLD_BLK)
            return 1;
        else
            return check_abnormal_runtime();
    }
    return 0;
}

static void set_timer()
{
    mod_timer(&timer, jiffies + msecs_to_jiffies(TIMEOUT));
}

static void signal_hang(unsigned long timer_data)
{
    /* print_metrics(&metrics); */
    printk(KERN_DEBUG "cande: hang detected. Panic now\n");
    panic("Hang detected ec: %d", ec);
}

module_init(start_heavy_detector);
module_exit(stop_heavy_detector);
