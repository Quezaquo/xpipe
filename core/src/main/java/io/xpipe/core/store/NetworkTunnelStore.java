package io.xpipe.core.store;

import io.xpipe.core.process.ShellControl;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public interface NetworkTunnelStore extends DataStore {

    AtomicInteger portCounter = new AtomicInteger();

    static int randomPort() {
        var p = 40000 + portCounter.get();
        portCounter.set(portCounter.get() + 1 % 1000);
        return p;
    }

    interface TunnelFunction {

        NetworkTunnelSession create(int localPort, int remotePort, String address) throws Exception;
    }

    DataStore getNetworkParent();

    default boolean requiresTunnel() {
        NetworkTunnelStore current = this;
        while (true) {
            var func = current.tunnelSession();
            if (func != null) {
                return true;
            }

            if (current.getNetworkParent() == null) {
                return false;
            }

            if (current.getNetworkParent() instanceof NetworkTunnelStore t) {
                current = t;
            } else {
                return false;
            }
        }
    }

    default boolean isLocallyTunnelable() {
        NetworkTunnelStore current = this;
        while (true) {
            if (current.getNetworkParent() == null) {
                return true;
            }

            if (current.getNetworkParent() instanceof NetworkTunnelStore t) {
                current = t;
            } else {
                return false;
            }
        }
    }

    default NetworkTunnelSession sessionChain(int local, int remotePort, String address) throws Exception {
        if (!isLocallyTunnelable()) {
            throw new IllegalStateException(
                    "Unable to create tunnel chain as one intermediate system does not support tunneling");
        }

        var counter = new AtomicInteger();
        var sessions = new ArrayList<NetworkTunnelSession>();
        NetworkTunnelStore current = this;
        do {
            var func = current.tunnelSession();
            if (func == null) {
                continue;
            }

            var currentLocalPort = isLast(current) ? local : randomPort();
            var currentRemotePort =
                    sessions.isEmpty() ? remotePort : sessions.getLast().getLocalPort();
            var t = func.create(currentLocalPort, currentRemotePort, current == this ? address : "localhost");
            t.start();
            sessions.add(t);
            counter.incrementAndGet();
        } while ((current = (NetworkTunnelStore) current.getNetworkParent()) != null);

        if (sessions.size() == 1) {
            return sessions.getFirst();
        }

        if (sessions.isEmpty()) {
            return new NetworkTunnelSession(null) {

                @Override
                public boolean isRunning() {
                    return false;
                }

                @Override
                public void start() {}

                @Override
                public void stop() {}

                @Override
                public int getLocalPort() {
                    return remotePort;
                }

                @Override
                public int getRemotePort() {
                    return remotePort;
                }

                @Override
                public ShellControl getShellControl() {
                    return null;
                }
            };
        }

        return new SessionChain(running1 -> {}, sessions);
    }

    default boolean isLast(NetworkTunnelStore tunnelStore) {
        NetworkTunnelStore current = tunnelStore;
        while ((current = (NetworkTunnelStore) current.getNetworkParent()) != null) {
            var func = current.tunnelSession();
            if (func != null) {
                return false;
            }
        }
        return true;
    }

    default TunnelFunction tunnelSession() {
        return null;
    }
}
