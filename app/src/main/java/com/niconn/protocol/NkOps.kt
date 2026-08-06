package com.niconn.protocol

object NkOps {
    const val GetDeviceInfo = 0x1001
    const val OpenSession = 0x1002
    const val CloseSession = 0x1003
    const val GetDevicePropDesc = 0x1014
    const val GetDevicePropValue = 0x1015
    const val SetDevicePropValue = 0x1016
    const val GetObjectHandles = 0x1007
    const val GetObjectInfo = 0x1008
    const val GetObject = 0x1009
    const val GetThumb = 0x100A
    const val DeleteObject = 0x100B
    const val SetDevicePropValueEx = 0x943C
    const val DeviceReady = 0x90C8
    const val ChangeCameraMode = 0x90C2
    const val AfDrive = 0x90C1
    const val ChangeAfArea = 0x9205
    const val InitiateCaptureRecInMedia = 0x9207
    const val StartLiveView = 0x9201
    const val EndLiveView = 0x9202
    const val GetLiveViewImageEx = 0x9428
    const val GetEventEx = 0x941C
    const val VENDOR_952B = 0x952B
    const val VENDOR_935A = 0x935A

    const val OK = 0x2001
    const val GENERAL_ERROR = 0x2002
    const val DEVICE_PROP_NOT_SUPPORTED = 0x200A
    const val DEVICE_BUSY = 0x2019
    const val OUT_OF_FOCUS = 0xA002
    const val NOT_LIVE_VIEW = 0xA00B
}
