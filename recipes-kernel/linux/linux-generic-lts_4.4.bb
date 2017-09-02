require linux.inc
require kselftests.inc

DESCRIPTION = "Generic Linux Stable 4.4"

PV = "4.4+git${SRCPV}"
SRCREV_kernel = "5fc13741fc3a013f67ed3c4dd70c9ed2e9ded971"
SRCREV_FORMAT = "kernel"

SRC_URI = "\
    git://git.linaro.org/people/sumit.semwal/linux-lts.git;protocol=https;nobranch=1;name=kernel \
    file://distro-overrides.config;subdir=git/kernel/configs \
    file://systemd.config;subdir=git/kernel/configs \
    file://0001-selftests-lib-add-config-fragment-for-bitmap-printf-.patch \
    file://0005-selftests-create-cpufreq-kconfig-fragments.patch \
    file://0001-selftests-sync-add-config-fragment-for-testing-sync-.patch \
    file://0002-4.4-selftests-ftrace-add-config-fragment.patch \
    file://0003-4.4-selftests-vm-add-config-fragment-fragment.patch \
    file://0004-4.4-selftests-firmware-add-config-fragment-fragment.patch \
    file://0005-4.4-selftests-static_keys-add-config-fragment-fragment.patch \
    file://0006-4.4-selftests-user-add-config-fragment-fragment.patch \
    file://0007-4.4-selftests-zram-add-config-fragment-fragment.patch \
"

S = "${WORKDIR}/git"

COMPATIBLE_MACHINE = "hikey"
KERNEL_IMAGETYPE ?= "Image"
KERNEL_CONFIG_FRAGMENTS += "\
    ${S}/kernel/configs/distro-overrides.config \
    ${S}/kernel/configs/systemd.config \
"

# make[3]: *** [scripts/extract-cert] Error 1
DEPENDS += "openssl-native"
HOST_EXTRACFLAGS += "-I${STAGING_INCDIR_NATIVE}"

do_configure() {
    # While kernel.bbclass has an architecture mapping, we can't use it because
    # the kernel config file has a different name.
    case "${HOST_ARCH}" in
      aarch64)
        cp ${S}/arch/arm64/configs/defconfig ${B}/.config
        echo 'CONFIG_CHECKPOINT_RESTORE=y' >> ${B}/.config
        echo 'CONFIG_EXPERT=y' >> ${B}/.config
        echo 'CONFIG_RTC_DRV_PL031=y' >> ${B}/.config
        echo 'CONFIG_STUB_CLK_HI6220=y' >> ${B}/.config
      ;;
      arm)
        cp ${S}/arch/arm/configs/multi_v7_defconfig ${B}/.config
      ;;
      x86_64)
        cp ${S}/arch/x86/configs/x86_64_defconfig ${B}/.config
      ;;
    esac

    # Check for kernel config fragments. The assumption is that the config
    # fragment will be specified with the absolute path. For example:
    #   * ${WORKDIR}/config1.cfg
    #   * ${S}/config2.cfg
    # Iterate through the list of configs and make sure that you can find
    # each one. If not then error out.
    # NOTE: If you want to override a configuration that is kept in the kernel
    #       with one from the OE meta data then you should make sure that the
    #       OE meta data version (i.e. ${WORKDIR}/config1.cfg) is listed
    #       after the in-kernel configuration fragment.
    # Check if any config fragments are specified.
    if [ ! -z "${KERNEL_CONFIG_FRAGMENTS}" ]; then
        for f in ${KERNEL_CONFIG_FRAGMENTS}; do
            # Check if the config fragment was copied into the WORKDIR from
            # the OE meta data
            if [ ! -e "$f" ]; then
                echo "Could not find kernel config fragment $f"
                exit 1
            fi
        done

        # Now that all the fragments are located merge them.
        ( cd ${WORKDIR} && ${S}/scripts/kconfig/merge_config.sh -m -r -O ${B} ${B}/.config ${KERNEL_CONFIG_FRAGMENTS} 1>&2 )
    fi

    # Since kselftest-merge target isn't available, merge the individual
    # selftests config fragments included in the kernel source tree
    ( cd ${WORKDIR} && ${S}/scripts/kconfig/merge_config.sh -m -r -O ${B} ${B}/.config ${S}/tools/testing/selftests/*/config 1>&2 )

    oe_runmake -C ${S} O=${B} olddefconfig

    bbplain "Saving defconfig to:\n${B}/defconfig"
    oe_runmake -C ${B} savedefconfig
}

do_deploy_append() {
    cp -a ${B}/defconfig ${DEPLOYDIR}
    cp -a ${B}/.config ${DEPLOYDIR}/config
}

require machine-specific-hooks.inc
