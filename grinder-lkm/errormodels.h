/*
 * errormodels.h
 *
 *  Created on: Sep 14, 2012
 *      Author: tretter
 */

#ifndef ERRORMODELS_H_
#define ERRORMODELS_H_

#include "grinder.h"

struct error_model {
	injector_t inject;                /* Inject fault */
	void (*configure) (const char *); /* Configure the error model */
	const char *name;                 /* Name of the error model */
};

extern struct error_model bitflip;
extern struct error_model logger;

#endif /* ERRORMODELS_H_ */
