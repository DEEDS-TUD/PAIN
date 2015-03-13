/*
 * tcpclient.c
 *
 *  Created on: Dec 13, 2011
 *      Author: Michael Tretter
 */

#include "tcpclient.h"

#include <asm/uaccess.h>
#include <linux/file.h>
#include <linux/in.h>
#include <linux/inet.h>
#include <linux/kernel.h>
#include <linux/net.h>
#include <linux/slab.h>
#include <linux/socket.h>
#include <linux/string.h>
#include <linux/tcp.h>
#include <net/sock.h>

static struct socket *sock;

static int connect1(void)
{
	struct sockaddr_in serv_addr;
	int error;

	error = sock_create(AF_INET, SOCK_STREAM, IPPROTO_TCP, &sock);
	if (sock < 0) {
		printk(KERN_ERR
		       "grinder-lkm: Cannot open TCP socket. Error: %d\n",
		       error);
		return error;
	}

	memset(&serv_addr, 0, sizeof(serv_addr));
	serv_addr.sin_family = AF_INET;
	/* TODO Do not use hard coded address! */
	serv_addr.sin_addr.s_addr = in_aton(HOST);
	serv_addr.sin_port = htons(PORT);

	error =
	    sock->ops->connect(sock, (struct sockaddr *)&serv_addr,
			       sizeof(serv_addr), O_RDWR);
	if (error < 0) {
		printk(KERN_ERR "Cannot connect to GRINDER server. Error: %d\n",
		       error);
		return error;
	}

	return 0;
}

static int check_socket(void)
{
	return sock != 0 ? 0 : connect1();
}

static void init_msg(struct msghdr *msg, struct iovec *iov, char *buf,
		     size_t size)
{
	msg->msg_name = 0;
	msg->msg_namelen = 0;
	msg->msg_iov = iov;
	msg->msg_iovlen = 1;
	msg->msg_control = NULL;
	msg->msg_controllen = 0;
	msg->msg_flags = MSG_DONTWAIT;

	msg->msg_iov->iov_len = size;
	msg->msg_iov->iov_base = buf;
}

int send_bytes(const char *buffer, size_t size)
{
	struct msghdr msg;
	struct iovec iov;
	mm_segment_t oldfs;
	int error, sent;

	if ((error = check_socket()))
		return error;

	init_msg(&msg, &iov, (char *)buffer, size);

	/* printk("Buffer: %d", buffer); */

	oldfs = get_fs();
	set_fs(KERNEL_DS);
	sent = sock_sendmsg(sock, &msg, size);
	set_fs(oldfs);
	/* FIXME: is this a bug?
	 * Shall the caller handle this? */
	BUG_ON(sent > 0 && sent != size);
	return sent == size ? 0 : sent;
}

int recv_bytes(char *buffer, size_t size)
{
	struct msghdr msg;
	struct iovec iov;
	mm_segment_t oldfs;
	int error, recv;

	if ((error = check_socket()))
		return error;

	init_msg(&msg, &iov, (char *)buffer, size);

	oldfs = get_fs();
	set_fs(KERNEL_DS);
	recv = sock_recvmsg(sock, &msg, size, 0);
	set_fs(oldfs);
	return recv;
}
