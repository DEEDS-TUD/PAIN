/*
 * tcpclient.h
 *
 *  Created on: Dec 13, 2011
 *      Author: Michael Tretter
 */

#ifndef TCPCLIENT_H_
#define TCPCLIENT_H_

#include <stddef.h>

#define PORT 4444
/* #define HOST "127.0.0.1" */
#define HOST "10.0.2.2" /* localhost on the host machine */

/**
 *  Send some bytes.
 *
 *  Returns zero on success or a negative value indicating the error.
 */
int send_bytes(const char *buffer, size_t size);
/**
 *  Receive some bytes.
 *
 *  Returns zero on success or a negative value indicating the error.
 */
int recv_bytes(char *buffer, size_t size);

#endif /* TCPCLIENT_H_ */
