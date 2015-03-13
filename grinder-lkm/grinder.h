/*
 * grinder.h
 *
 *  Created on: Mar 9, 2012
 *      Author: Michael Tretter
 */

#ifndef GRINDER_H_
#define GRINDER_H_

#include <linux/kernel.h>

#define KSERVICE_SYMBOL_SIZE 32

struct arg {
	char *val;
	size_t size;
};

struct service {
	char *symbol;
	struct arg *args;
	unsigned int arg_cnt;
};

struct victim {
	unsigned int interceptor_id; /* Creator of the Victim */
	struct service *location;    /* First byte of the injection location */
};

typedef int (*injector_t)(const struct victim *);

injector_t get_injector(int id);

#endif /* GRINDER_H_ */
