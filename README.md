## 本工程为MM美颜SDK接入第三方直播、视频sdk demo
> ### 接入MM美颜sdk需要修改的部分：
> - applicationId ""      // 配置cosmos后台配置的applicationid
> - signingConfigs.config //配置cosmos后台配置的keystore
> - cosmosAppid.          //需要修改为cosmos后台注册的appid


### MMAgoraDemoDroid 为接入声网1v1聊天sdk demo
### MMZegoExpress 为接入zego自定义视频前处理 demo
### MMQiniu 为接入七牛sdk demo
### MMTencent 为接入腾讯直播sdk demo
> MMTencent除需要配置以上三个部分之外，还需要在TxApplication中配置在腾讯直播后台申请的licenceUrl以及licenseKey


