obj-m := grinder.o

grinder-objs := grinder_main.o 
grinder-objs += comm.o 
grinder-objs += bitflip.o 
grinder-objs += log_victim.o 
grinder-objs += tcpclient.o

#EXTRA_CFLAGS= -Wall -pedantic

#C_INCLUDE_PATH=/home/grinder/Workspace/grinder/lib/core/src/main/include

all: checkenv
	make C_INCLUDE_PATH=$(C_INCLUDE_PATH) -C $(AFI_KERNEL_HOME) M=$(PWD)

clean: checkenv
	make -C $(AFI_KERNEL_HOME) M=$(PWD) clean

checkenv:
ifndef AFI_HOME
	@echo "ERROR: Environment setup invalid."
	@echo "Please execute the setup script first."
	exit 1
endif
	
