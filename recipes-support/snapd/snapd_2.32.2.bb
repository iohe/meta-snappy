SUMMARY = "The snapd and snap tools enable systems to work with .snap files."
HOMEPAGE = "https://www.snapcraft.io"
LICENSE = "GPL-3.0"
LIC_FILES_CHKSUM = "file://${WORKDIR}/${PN}-${PV}/COPYING;md5=d32239bcb673463ab874e80d47fae504"

SRC_URI = "									\
	https://${GO_IMPORT}/releases/download/${PV}/snapd_${PV}.vendor.tar.xz	\
"

SRC_URI[md5sum] = "cdb72d9110cbbc0a3a7a3896d4a100cb"
SRC_URI[sha256sum] = "21e27119da2a04d670860c9edbb0d28d190c4ff67fa1a1dc517e0278fe95f2b5"

GO_IMPORT = "github.com/snapcore/snapd"

SHARED_GO_INSTALL = "				\
	${GO_IMPORT}/cmd/snap		\
	${GO_IMPORT}/cmd/snapd		\
	${GO_IMPORT}/cmd/snapctl	\
	${GO_IMPORT}/cmd/snap-seccomp	\
	"

STATIC_GO_INSTALL = " \
	${GO_IMPORT}/cmd/snap-exec		\
	${GO_IMPORT}/cmd/snap-update-ns		\
"

GO_INSTALL = "${SHARED_GO_INSTALL}"

DEPENDS += "			\
	go-cross-${TUNE_PKGARCH}	\
	glib-2.0		\
	udev			\
	xfsprogs		\
	libseccomp \
"

RDEPENDS_${PN} += "		\
	ca-certificates		\
	kernel-module-squashfs	\
	bash \
"

S = "${WORKDIR}/${PN}-${PV}"

EXTRA_OECONF += "			\
	--disable-apparmor		\
	--enable-seccomp		\
	--libexecdir=${libdir}/snapd	\
	--with-snap-mount-dir=/snap \
"

inherit systemd autotools pkgconfig go

# disable shared runtime for x86,arm
# https://forum.snapcraft.io/t/yocto-rocko-core-snap-panic/3261
# GO_DYNLINK is set with arch overrides in goarch.bbclass
GO_DYNLINK_x86 = ""
GO_DYNLINK_arm = ""

# Our tools build with autotools are inside the cmd subdirectory
# and we need to tell the autotools class to look in there.
AUTOTOOLS_SCRIPT_PATH = "${S}/cmd"

SYSTEMD_SERVICE_${PN} = "snapd.service"

do_configure_prepend() {
	(cd ${S} ; ./mkversion.sh ${PV})
}

# The go class does export a do_configure function, of which we need
# to change the symlink set-up, to target snapd's environment.
do_configure() {
	mkdir -p ${B}/src/github.com/snapcore
	ln -snf ${S} ${B}/src/${GO_IMPORT}
	autotools_do_configure
}

CFLAGS += "-Wno-cast-function-type"
CXXFLAGS += "-Wno-cast-function-type"

do_compile() {
	go_do_compile
	# these *must* be built statically
	for prog in ${STATIC_GO_INSTALL}; do
		${GO} install -v \
		        -ldflags="${GO_RPATH} -extldflags '${HOST_CC_ARCH}${TOOLCHAIN_OPTIONS} ${GO_RPATH_LINK} ${LDFLAGS} -static'" \
		        $prog
	done

  # build the rest
  autotools_do_compile
}

do_install() {
	install -d ${D}${libdir}/snapd
	install -d ${D}${bindir}
	install -d ${D}${systemd_unitdir}/system
	install -d ${D}/var/lib/snapd
	install -d ${D}/var/lib/snapd/snaps
	install -d ${D}/var/lib/snapd/lib/gl
	install -d ${D}/var/lib/snapd/desktop
	install -d ${D}/var/lib/snapd/environment
	install -d ${D}/var/snap
	install -d ${D}${sysconfdir}/profile.d
  install -d ${D}${systemd_unitdir}/system-generators

	oe_runmake -C ${B} install DESTDIR=${D}
	oe_runmake -C ${S}/data/systemd install \
		DESTDIR=${D} \
		BINDIR=${bindir} \
		LIBEXECDIR=${libdir} \
		SYSTEMDSYSTEMUNITDIR=${systemd_system_unitdir} \
		SNAP_MOUNT_DIR=/snap \
		SNAPD_ENVIRONMENT_FILE=${sysconfdir}/default/snapd

  mv ${D}${libdir}/snapd/snapd-generator ${D}${systemd_unitdir}/system-generators/

	install -m 0755 ${B}/${GO_BUILD_BINDIR}/snapd ${D}${libdir}/snapd/
	install -m 0755 ${B}/${GO_BUILD_BINDIR}/snap-exec ${D}${libdir}/snapd/
	install -m 0755 ${B}/${GO_BUILD_BINDIR}/snap-seccomp ${D}${libdir}/snapd/
	install -m 0755 ${B}/${GO_BUILD_BINDIR}/snap-update-ns ${D}${libdir}/snapd/
	install -m 0755 ${B}/${GO_BUILD_BINDIR}/snap ${D}${bindir}
	install -m 0755 ${B}/${GO_BUILD_BINDIR}/snapctl ${D}${bindir}

	echo "PATH=\$PATH:/snap/bin" > ${D}${sysconfdir}/profile.d/20-snap.sh
}

RDEPENDS_${PN} += "squashfs-tools"
FILES_${PN} += "                        \
	${systemd_unitdir}/system/            \
	${systemd_unitdir}/system-generators/	\
	/var/lib/snapd                        \
	/var/snap                             \
	${baselib}/udev/snappy-app-dev        \
"

# ERROR: snapd-2.23.5-r0 do_package_qa: QA Issue: No GNU_HASH in the elf binary:
# '.../snapd/usr/lib/snapd/snap-exec' [ldflags]
INSANE_SKIP_${PN} = "ldflags"
PTEST_ENABLED = "0"
