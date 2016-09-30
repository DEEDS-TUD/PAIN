/* defines constants for the light-/heavydetector */
#ifndef CANDE_COMMONS_H
#define CANDE_COMMONS_H

/* enable debug output */
/* #define CANDE_DEBUG */


struct detection_metrics {
	unsigned int num_cpus;
	struct cpu_metrics *cpu;
	unsigned int cs;
	unsigned int pswpout;
	unsigned int memfree;
	unsigned int run;
	unsigned int blk;
};

struct cpu_metrics {
	unsigned int sys;
	unsigned int usr;
	unsigned int iowait;
};

/* error classes */
#define EC_NONE 0
#define EC_CPU_ERROR 1
#define EC_MEM_ERROR 2
#define EC_PROC_ERROR 3
#define EC_HELLO_SERVER 4

#define NETLINK_NITRO 1

#endif
