CPPFLAGS:=-I/usr/src/linux-headers-$(shell uname -r)
CFLAGS:=-Wall -Wextra -std=gnu90
SUDO:=sudo

all: getdelays

clean:
	rm getdelays

debug: CFLAGS += -g
debug: getdelays

getdelays: getdelays.c
	$(LINK.c) $^ -o $@
	$(SUDO) setcap cap_net_admin+ep $@
