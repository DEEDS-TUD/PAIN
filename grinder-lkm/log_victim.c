/*
 * log_victim.c
 *
 *  Created on: Apr 12, 2012
 *      Author: Michael Tretter
 */

#include "errormodels.h"
#include "grinder.h"
#include "comm.h"

/* #include <linux/kernel.h> */

/* Logging */
#define BUFSIZE 512

static char message[BUFSIZE];

static int inject(const struct victim *victim)
{
	int i;

	/* for testing purpose */
	for (i = 0; i < victim->location->arg_cnt; i++) {
		snprintf(message, BUFSIZE,
			 "(KService: %s, Param_pos: %d, Param_len: %d)",
			 victim->location->symbol, i,
			 victim->location->args[i].size);
		send_log(message);
	}

	return 0;
}

static void configure(const char *configuration)
{
	/* Currently no configuration possible */
	return;
}

struct error_model logger = {
	inject,
	configure,
	"Logger"
};
