/*
 * comm.c
 *
 *  Created on: Feb 13, 2012
 *      Author: Michael Tretter
 */

#include "comm.h"

#include "tcpclient.h"
#include <asm/byteorder.h>
#include <linux/string.h>
#include <linux/module.h>

/* see de.grinder.util.message.MessageTypes (Java) */
#define TYPE_LOG                      3
#define TYPE_GET_INJECTIONPARAMETERS  5
#define TYPE_SEND_INJECTIONPARAMETERS 6

#define CHECKED_TCP_CALL(fun, ...)					       \
	do {								       \
		int error = fun(__VA_ARGS__);				       \
		if(error < 0) {						       \
			printk(#fun " returned with an error: %d.\n", error);  \
			return -1;					       \
		}							       \
	} while(0)

struct network_header {
	short type;
	short runid;
	short size;
} __packed;

struct header {
	short type;
	short runid;
	short size;
};

static int send_header(const struct header *header)
{
	struct network_header network_header = {
		htons(header->type ),
		htons(header->runid),
		htons(header->size )
	};

	CHECKED_TCP_CALL(send_bytes, (char *)&network_header,
			 sizeof(network_header));

	return 0;
}

static int recv_header(struct header *header)
{
	struct network_header network_header;
	int recv;

	CHECKED_TCP_CALL(recv = recv_bytes, (char *)&network_header,
			 sizeof(network_header));

	if (recv != sizeof(network_header)) {
		printk("recv_header: header too short.\n");
		return -1;
	}

	header->type  = ntohs(network_header.type);
	header->runid = ntohs(network_header.runid);
	header->size  = ntohs(network_header.size);

	return 0;
}

int send_log(const char *message)
{
	size_t length = strlen(message);
	struct header header = {
		TYPE_LOG,
		0,
		(short)length
	};

	if (length > SHRT_MAX) {
		printk("message '%s' longer than %d.\n", message, SHRT_MAX);
		return -1;
	}

	/* send message */
	if (send_header(&header))
		return -1;
	CHECKED_TCP_CALL(send_bytes, message, length);

	return 0;
}

int get_configuration(char *configuration, size_t size)
{
	struct header header = {
		TYPE_GET_INJECTIONPARAMETERS,
		0,
		0
	};
	int recv;

	/* send request */
	if (send_header(&header))
		return -1;

	/* receive header of answer */
	if (recv_header(&header))
		return -1;

	/* sanity checks */
	if (header.type != TYPE_SEND_INJECTIONPARAMETERS) {
		printk("Unexpected message type %d received while requesting injection parameters.\n",
		     header.type);
		return -1;
	}
	if (header.size > size) {
		printk("Message size %d > configuration buffer size %d.\n",
		       header.size, size);
		return -1;
	}

	/* receive configuration */
	CHECKED_TCP_CALL(recv = recv_bytes, configuration, header.size);
	if (recv < header.size)
		printk("Warning: Received configuration shorter than announced.\n");

	return 0;
}
