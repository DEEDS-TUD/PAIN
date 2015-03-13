/*
 * grinder_main.c
 *
 *  Created on: Feb 14, 2013
 *      Author: Michael Tretter
 */

#include "grinder.h"

#include <linux/init.h>
#include <linux/kernel.h>
#include <linux/module.h>
#include <stdbool.h>

#include "comm.h"
#include "errormodels.h"

MODULE_LICENSE("GPL");

/*
 * Data structure for storing the mapping of interceptors and injectors.
 * Currently, we use only a very simple mapping using a array and its indices,
 * but as the data structure is hidden behind get_injector, it can change
 * without affecting existing interceptors.
 */
static const struct error_model *errormodels[] = { &bitflip, &logger };

static bool configured = false;

static void setup(int id)
{
	/* 5 = 4 for bit and param location + 1 for a message specific
	 * 1 byte prefix */
	char message[KSERVICE_SYMBOL_SIZE + 5];
	char *configuration;

	get_configuration(message, sizeof(message));
	/* the first letter is message specific and doesn't belong to the
	 * configuration itself */
	configuration = &message[1];

	errormodels[id]->configure(configuration);
	configured = true;
}

injector_t get_injector(int id)
{
	if (!configured)
		setup(id);

	return errormodels[id]->inject;
}

EXPORT_SYMBOL(get_injector);

static int __init grinder_init(void)
{
	printk(KERN_INFO "GRINDER loaded!\n");
	return 0;
}

static void __exit grinder_exit(void)
{
	printk(KERN_INFO "GRINDER unloaded!\n");
}

module_init(grinder_init);
module_exit(grinder_exit);
