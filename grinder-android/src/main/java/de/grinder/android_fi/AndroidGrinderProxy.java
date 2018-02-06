package de.grinder.android_fi;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;

import de.grinder.util.message.Message;
import de.grinder.util.message.MessageType;
import de.grinder.util.message.Proxy;

class AndroidGrinderProxy extends Proxy {
    private String conf = null;

    public AndroidGrinderProxy(final String host, final int port) {
        super(host, port);
    }

    public File getInstrumentedKernelModule() throws IOException {
        conf = getConfiguration();
        return new File(conf.split(" ")[0]);
    }

    public void sendLog(final String logData) throws IOException {
        final Message request = new Message(MessageType.LOG);
        request.setBody(logData.getBytes());
        final OutputStream out = getSocketOutStream();
        out.write(request.getBytes());
        out.flush();
    }
}
