module.exports = {
    flowFile: 'flows.json',
    uiPort: process.env.PORT || 1880,
    mqttReconnectTime: 15000,
    serialReconnectTime: 15000,
    debugMaxLength: 1000,
    functionGlobalContext: {},
    editorTheme: {
        page: { title: "MHD Dashboard" },
        header: { title: "Machine Overheating Detection" }
    }
};
