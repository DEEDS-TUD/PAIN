/*
 * sar_parser.c
 *
 *  Created on: 28.08.2013
 *      Author: root
 */
#include "sar_parser.h"

#include <stdarg.h>
#include <stdio.h>

static int skip_line(FILE *sar_output)
{
	if (fgetc(sar_output) != '\n') {
		char s[512];
		fscanf(sar_output, "%[^\n]", s);
		/* swallow \n */
		fgetc(sar_output);
	}

	if (ferror(sar_output))
		return 0;

	/* remove the first 8 characters from output since they indicate the
	 * current time and are not interesting for us */
	if (fgetc(sar_output) != '\n') {
		int i;
		char c;

		/* the following doesn't work - work around with fgetc-loop */
		/* fseek(sar_output, 7, SEEK_CUR); */
		for (i = 0; i < 6; ++i)
			fgetc(sar_output);

		/* skip space */
		fscanf(sar_output, " %c", &c);
		ungetc(c, sar_output);
	} else {
		/* if we had an empty line, put it back in the buffer */
		ungetc('\n', sar_output);
	}

	if (ferror(sar_output))
		return 0;

	return 1;
}

static int parse_simple(FILE *sar_output, const char *line_id, int nargs,
			const char *fmt, ...)
{
	va_list args;
	int ret;

	if (fscanf(sar_output, line_id) == EOF)
		return 0;

	/* skip rest of line */
	if (!skip_line(sar_output))
		return 0;

	va_start(args, fmt);
	ret = vfscanf(sar_output, fmt, args);
	va_end(args);
	if (ret != nargs)
		return 0;

	/* skip rest of line */
	if (!skip_line(sar_output))
		return 0;

	/* skip empty line */
	if (fgetc(sar_output) != '\n')
		return 0;

	return 1;
}

int init_parser(FILE *sar_output)
{
	int i;
	/* first two lines are uninteresting */
	for (i = 0; i < 2; ++i) {
		if (!skip_line(sar_output))
			return 0;
	}

	return 1;
}

int parse_metrics(FILE *sar_output, struct detection_metrics *metrics)
{
	unsigned int i;
	/* some output is parsed as float, but we store integers */
	float f;

	/* CPU */
	if (fscanf(sar_output, " CPU") == EOF)
		return 0;

	/* skip rest of line */
	if (!skip_line(sar_output))
		return 0;

	/* first line uninteresting (all CPUs) */
	if (!skip_line(sar_output))
		return 0;

	for (i = 0; i < metrics->num_cpus; ++i) {
		int cpu_num;
		float usr, sys, iowait;
		struct cpu_metrics *cur_cpu;

		if (fscanf(sar_output, "%d %f %*f %f %f",
			  &cpu_num, &usr, &sys, &iowait) != 4) {
		/*  || cur_cpu != i) { */
			return 0;
		}

		/* skip rest of line */
		if (!skip_line(sar_output))
			return 0;

		cur_cpu = &metrics->cpu[cpu_num];
		cur_cpu->usr    = usr;
		cur_cpu->sys    = sys;
		cur_cpu->iowait = iowait;
	}

	/* skip empty line */
	if (fgetc(sar_output) != '\n')
		return 0;

	/* CSWITCH */
	if (!parse_simple(sar_output, "proc/s", 1, "%*f %f", &f))
		return 0;
	metrics->cs = f;

	/* PAGESWAP */
	if (!parse_simple(sar_output, "pswpin/s", 1, "%*f %f", &f))
		return 0;
	metrics->pswpout = f;

	/* MEMFREE */
	if (!parse_simple(sar_output, "kbmemfree", 1, "%d", &metrics->memfree))
		return 0;

	/* TASKS */
	if (!parse_simple(sar_output, "runq_sz", 2, "%d %*d %*f %*f %*f %d",
			  &metrics->run, &metrics->blk))
		return 0;

	return 1;
}
