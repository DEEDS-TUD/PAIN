LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE    := randmemlib
LOCAL_SRC_FILES := randmem.c
include $(BUILD_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE    := drivespeedlib
LOCAL_SRC_FILES := drivespeed.c
include $(BUILD_SHARED_LIBRARY)
