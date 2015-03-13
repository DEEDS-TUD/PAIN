/*
 * sar_parser.h
 *
 *  Created on: 28.08.2013
 *      Author: root
 */
#ifndef SAR_PARSER_H
#define SAR_PARSER_H

#include "../cande_commons.h"

#include <stdio.h>

/* returns whether or not it succeeded */
int init_parser(FILE *sar_output);
/* returns whether or not it succeeded */
int parse_metrics(FILE *, struct detection_metrics *);

#endif
