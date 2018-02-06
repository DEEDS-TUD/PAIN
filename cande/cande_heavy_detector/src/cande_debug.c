#include "../cande_commons.h"

#include <linux/kernel.h>

#ifdef CANDE_DEBUG
void print_metrics(struct detection_metrics *metrics)
{
    int i;
    for (i = 0; i < metrics->num_cpus; i++) {
        struct cpu_metrics *cpu = &metrics->cpu[i];
        printk(KERN_ALERT "cande: CPU %d: usr: %d, sys: %d, iowait: %d\n",
               i, cpu->usr, cpu->sys, cpu->iowait);
    }
    printk(KERN_ALERT
           "cande: cs: %d, pgswaps: %d, memfree: %d , blocked-procs: %d, running-procs: %d\n",
           metrics->cs, metrics->pswpout, metrics->memfree, metrics->blk,
           metrics->run);
}

void print_data_as_hex(char *data, int len)
{
    int i;
    for (i = 0; i < len; ++i) {
        printk(KERN_DEBUG "cande: data %d: %x\n", i, data[i]);
    }
}
#else

#define print_metrics(dummy)
#define print_data_as_hex(dummy1, dummy2)

#endif
