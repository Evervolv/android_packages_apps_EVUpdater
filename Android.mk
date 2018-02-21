LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

appcompat_dir := ../../../prebuilts/sdk/current/support/v7/appcompat/res
res_dir := res $(appcompat_dir)

LOCAL_MODULE_TAGS := optional

LOCAL_STATIC_JAVA_LIBRARIES += \
    android-support-v13 \
    android-support-v4 \
    android-support-v7-appcompat

LOCAL_RESOURCE_DIR := $(addprefix $(LOCAL_PATH)/, $(res_dir))
LOCAL_SRC_FILES := $(call all-java-files-under, src/)

LOCAL_AAPT_FLAGS := --auto-add-overlay
LOCAL_AAPT_FLAGS += --extra-packages android.support.v7.appcompat

LOCAL_PACKAGE_NAME := EVUpdater
LOCAL_CERTIFICATE := platform
LOCAL_PRIVATE_PLATFORM_APIS := true
LOCAL_PRIVILEGED_MODULE := true

include $(BUILD_PACKAGE)
