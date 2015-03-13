obj-m := cande_heavy_detector.o 
cande_heavy_detector-objs := ./src/cande_heavy_detector.o ./src/taskutil.o

EXTRA_CFLAGS := -Wall -fno-pic
KERNELDIR=/lib/modules/$(shell uname -r)/build
PWD=$(shell pwd)

linux:
	make -C $(KERNELDIR) M=$(PWD) modules

android: checkenv
	make C_INCLUDE_PATH=$(C_INCLUDE_PATH) -C $(AFI_KERNEL_HOME) M=$(PWD) 

clean:
	rm -rf *.o *.ko *~ core .depend .tmp_versions .*.cmd *.mod.c
.PHONY: modules modules_install clean

linux_install:
	make -C $(KERNELDIR) M=$(PWD) modules_install

checkenv:
ifndef AFI_HOME
	@echo "ERROR: Environment setup invalid."
	@echo "Please execute the setup script first."
	exit 1
endif

