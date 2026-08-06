# 尼康 Z50II 无线控制协议说明（结论）

## 连接模式

- 相机菜单：「连接至智能设备」+ Wi-Fi 连接 (STA 模式)，相机以客户端身份连接手机热点。
- 支持「连接到计算机」模式作为回退（Init code 不同）。

## 设备发现

- mDNS 服务类型 `_ptp._tcp.local`，实例名 `<型号>_<序列号>`（如 `Z50_2_8095684`）。
- SRV 端口：15740；TXT：`guid`（与 Init ACK 一致）、`vid`、`pid`、`apps`（`PAIR`/`$DSC`）。

## 帧格式（TCP 15740）

- 帧头 8 字节小端：`length(4) + packet_type(4)`，长度在前。
- 类型：1/2 Init Command（请求/ACK）、3/4 Init Event、6/7 命令（请求/响应）、8 事件、9/10/12 数据阶段（Start/Data/End）、13/14 保活。

## 会话建立

1. 命令通道：`type1` Init Command Request → `type2` ACK（返回 session=1、相机 GUID、型号+序列号）。
2. 事件通道：第二条 TCP 连接发 `type3` → `type4` ACK。
3. 命令通道按序发送 GetDeviceInfo（0x1001）、OpenSession（0x1002，参数 2）。

### Init Command Request 载荷

`客户端 UUID(16B) + 设备名(UTF-16) + 00 00 + bufferSize(4LE=65536)`。

- 智能设备模式：UUID 第 5-6 字节 `61 1e`，设备名 UTF-16BE（如 `Android Device`）。
- 计算机模式：UUID 第 5-6 字节 `0d cc`，设备名 UTF-16LE（如 `WTU-<主机名>`）。
- 客户端 UUID 持久化复用；命令会话号以 ACK 返回为准（恒为 1）。

## 配对

1. 会话内发送 GetPtpipPairingCode（0x952B）。
2. 响应数据 = `count(4LE) + count 个字节`，每字节十进制拼接即验证码（例：`05 00 07 07` → `5077`）。
3. 发送 CompletePairing（0x935A，参数 0x2001）完成配对。
4. 配对完成后相机会自行结束会话；已配对客户端重连时跳过配对码流程。

## 常用命令（操作码与 USB MTP 一致）

| 操作码 | 命令 | 说明 |
| --- | --- | --- |
| 0x1007 | GetObjectHandles | 参数：存储、格式、关联；响应为句柄数组 |
| 0x1008 | GetObjectInfo | ObjectInfo 数据集 |
| 0x1009 / 0x100A | GetObject / GetThumb | 下载原图 / 缩略图 |
| 0x100B | DeleteObject | 删除 |
| 0x1014 | GetDevicePropDesc | 属性枚举（实际可选值） |
| 0x1015 / 0x1016 | Get/SetDevicePropValue | 读写 2 字节属性码，数据为裸值字节 |
| 0x943C | SetDevicePropValueEx | 4 字节扩展属性码 |
| 0x9201 / 0x9202 | Start/EndLiveView | 远程取景开关 |
| 0x9205 | ChangeAfArea | 点按对焦坐标（见下） |
| 0x9207 | InitiateCaptureRecInMedia | 参数 `0xFFFFFFFF, 0`；完成后轮询 DeviceReady |
| 0x90C1 / 0x90C8 | AfDrive / DeviceReady | 对焦驱动 / 操作完成轮询 |
| 0x9428 | GetLiveViewImageEx | 轮询取景帧（约 10fps） |

## 数据阶段（host → camera 写数据）

`type6 请求 → type9 Start Data(session+length8) → type12 End Data(session+data) → type7 响应`。

## 设备属性

- ISO：0x500F（UINT16，值即 ISO）
- 快门：0x500D（UINT32，1/10000s）
- 光圈：0x5007（UINT16，光圈×100）
- 曝光补偿：0x5010（INT16，EV×1000）
- AF 区域模式：0xD05D（UINT16；单点=0x8010）
- DevicePropValue 数据为裸值字节（无 DataType 前缀）

## LiveView

- 数据：LiveViewObject（头部 1024 字节 + JPEG），JPEG 分辨率 640×424。
- 头部多字节字段为大端：Whole size 5568×3712、图像尺寸 640×424、AF 框（尺寸/中心，偏移 48-55）、对焦状态（偏移 41/42）。
- ChangeAfArea 坐标按整幅坐标发送，本机校准系数为 JPEG 像素 × 8.0。
- 0x2019（Device_Busy）需退避重试；0xA00B（Not_LiveView）需重新 StartLiveView。

## 相册 / ObjectInfo

- ObjectInfo 多字节字段为小端；固定区 52 字节（AssociationDesc 占 4 字节），随后是文件名、拍摄日期等 UTF-16 字符串。
- 对象格式码：JPEG=0x3801，NEF=0x3000（MTP 支持时）/0x3800。
- 缩略图：GetThumb；原图：GetObject（大文件需流式累积，避免内存峰值）。

## 响应码

- 0x2001 OK、0x2002 General Error、0x200A 属性不支持、0x2019 Device_Busy、0xA002 Out of Focus、0xA00B Not_LiveView。
