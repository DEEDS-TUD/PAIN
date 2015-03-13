/*
 * bitflip.c
 *
 *  Created on: Apr 12, 2012
 *      Author: Michael Tretter
 */

#include "grinder.h"
#include "errormodels.h"

#include <stdbool.h>
#include <linux/kernel.h>
#include <linux/string.h>

#define STRINGIFY(x) _STRINGIFY(x)
#define _STRINGIFY(x) #x

/* The position of the bit to be flipped. */
static unsigned short target_bit;
static unsigned short param_pos;
static char kservice_symbol[KSERVICE_SYMBOL_SIZE];

static bool configured = false;

static int inject(const struct victim *victim)
{
	unsigned int pos_byte;
	unsigned int pos_bit;
	unsigned char *flip;
	const struct arg *target_argument;

	if (!configured)
		/* Do nothing, because error model is not configured. */
		return -1;

	if (strncmp(victim->location->symbol, kservice_symbol,
		    KSERVICE_SYMBOL_SIZE))
		/* Do nothing, because the current service is not the one
		 * targeted by the current testcase. */
		return -1;

	target_argument = &victim->location->args[param_pos];

	if (target_bit >= target_argument->size * 8) {
		printk
		    ("Bit %d is outside of parameter %d's bit size which is %d.\n",
		     target_bit, param_pos, target_argument->size * 8);
		return -1;
	}

	pos_byte = target_bit / 8;
	pos_bit = target_bit % 8;

	/* printk("Injecting in param %d, bit pos %d in service %."
	 *        STRINGIFY(KSERVIC_SYMBOL_SIZE) "s\n", param_pos, target_bit,
	 *        kservice_symbol); */

	flip = target_argument->val + pos_byte;
	*flip ^= 1 << pos_bit;

	return 0;
}

static void configure(const char *configuration)
{
	const short *sconfig =
		(const short *)(configuration + KSERVICE_SYMBOL_SIZE);
	strncpy(kservice_symbol, configuration, KSERVICE_SYMBOL_SIZE);
	param_pos  = sconfig[0];
	target_bit = sconfig[1];
	configured = true;
}

struct error_model bitflip = {
	inject,
	configure,
	"Bitflip"
};
