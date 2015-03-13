#include "../cande_commons.h"

#include <stdio.h>

#ifdef CANDE_DEBUG
void print_metrics(struct detection_metrics *metrics)
{
	int i;
	for (i = 0; i < metrics->num_cpus; i++) {
		struct cpu_metrics *cpu = &metrics->cpu[i];
		printf("CPU %d: usr: %d, sys: %d, iowait: %d\n",
		       i, cpu->usr, cpu->sys, cpu->iowait);
	}
	printf(": cs: %d, pgswaps: %d, memfree: %d , blocked-procs: %d, running-procs: %d\n",
	       metrics->cs, metrics->pswpout, metrics->memfree, metrics->blk,
	       metrics->run);
}

void print_data_as_hex(char *data, int len)
{
	int i;
	for (i = 0; i < len; ++i) {
		printf("data %d: %x\t", i, data[i]);
	}
	printf("\n");
}
#else

#define print_metrics(dummy)
#define print_data_as_hex(dummy1, dummy2)

#endif
