ANDROID_GCC=$(AFI_NDK_TOOLCHAIN)/bin/$(AFI_NDK_HOST)-gcc
LINUX_GCC=gcc
CFLAGS=-Wall -Wextra -std=gnu90 -s -fPIE -pie

OUTEXE=cande_light_detector

linux:
	$(LINUX_GCC) $(CFLAGS) -o $(OUTEXE) light_detector.c sar_parser.c

android: checkenv
	$(ANDROID_GCC) $(CFLAGS) --sysroot=$(AFI_NDK_SYSROOT) -o $(OUTEXE) light_detector.c sar_parser.c

clean:
	rm -f *.o *.so $(OUTEXE) 

checkenv:
ifndef AFI_HOME
	@echo "ERROR: Environment setup invalid."
	@echo "Please execute the setup script first."
	exit 1
endif

