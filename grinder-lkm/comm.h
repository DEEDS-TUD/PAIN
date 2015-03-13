/*
 * comm.h
 *
 *  Created on: Feb 13, 2012
 *      Author: Michael Tretter
 */

/*
 * The comm module provides convenience methods for sending and receiving
 * messages.
 */

#include <stddef.h>

#ifndef COMM_H_
#define COMM_H_

int send_log(const char *message);
int get_configuration(char *configuration, size_t size);

#endif /* COMM_H_ */
