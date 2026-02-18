package com.flappyboldo.game.server;

import com.flappyboldo.game.online.net.OnlineProtocol;
import com.flappyboldo.game.online.net.OnlineServer;

import javax.swing.DefaultListModel;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;

public final class ServerMain {
    private ServerMain() {
    }

    public static void main(String[] args) throws Exception {
        int port = parsePort(args);
        OnlineServer server = startServer(port);

        Runtime.getRuntime().addShutdownHook(new Thread(server::closeServer));

        SwingUtilities.invokeLater(() -> createAndShowMonitor(server, port));
        server.join();
    }

    private static int parsePort(String[] args) {
        if (args == null || args.length == 0) {
            return OnlineProtocol.PORT;
        }
        try {
            int value = Integer.parseInt(args[0].trim());
            if (value < 1 || value > 65535) {
                throw new IllegalArgumentException("Port must be between 1 and 65535");
            }
            return value;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid port argument. Usage: server.jar [port]");
        }
    }

    private static OnlineServer startServer(int port) throws SocketException {
        OnlineServer server = new OnlineServer(port);
        server.setName("online-udp-server");
        server.start();
        System.out.println("Online server started on UDP port " + server.getPort() + ".");
        return server;
    }

    private static void createAndShowMonitor(OnlineServer server, int port) {
        JFrame frame = new JFrame("FlappyBird UDP Server - Port " + port);
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        String serverIp = resolveLocalIp();
        JLabel serverIpLabel = new JLabel("Server IP: " + serverIp + ":" + port);
        JLabel roomStateLabel = new JLabel("Room state: " + OnlineProtocol.ROOM_WAITING);
        JLabel lastWinnerLabel = new JLabel("Last winner: -");

        DefaultListModel<String> playerModel = new DefaultListModel<>();
        JList<String> playerList = new JList<>(playerModel);

        JPanel topPanel = new JPanel(new GridLayout(3, 1));
        topPanel.add(serverIpLabel);
        topPanel.add(roomStateLabel);
        topPanel.add(lastWinnerLabel);

        frame.setLayout(new BorderLayout(8, 8));
        frame.add(topPanel, BorderLayout.NORTH);
        frame.add(new JScrollPane(playerList), BorderLayout.CENTER);

        Timer timer = new Timer(250, event -> {
            OnlineServer.ServerSnapshot snapshot = server.getSnapshot();
            roomStateLabel.setText("Room state: " + snapshot.roomState);

            String winnerText = "-";
            if (snapshot.lastWinnerId > 0) {
                for (OnlineServer.PlayerSnapshot player : snapshot.players) {
                    if (player.id == snapshot.lastWinnerId) {
                        winnerText = "P" + player.id + " - " + player.name;
                        break;
                    }
                }
                if ("-".equals(winnerText)) {
                    winnerText = "P" + snapshot.lastWinnerId;
                }
            } else if (OnlineProtocol.ROOM_FINISHED.equals(snapshot.roomState)) {
                winnerText = "Draw";
            }
            lastWinnerLabel.setText("Last winner: " + winnerText);

            playerModel.clear();
            if (snapshot.players.isEmpty()) {
                playerModel.addElement("No players connected");
            } else {
                for (OnlineServer.PlayerSnapshot player : snapshot.players) {
                    playerModel.addElement(
                        "P" + player.id
                            + " - " + player.name
                            + " | score=" + player.score
                            + " | " + (player.ready ? "READY" : "WAIT")
                            + " | " + (player.alive ? "ALIVE" : "OUT")
                    );
                }
            }
        });
        timer.start();

        frame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent e) {
                timer.stop();
                server.closeServer();
            }
        });

        frame.setSize(520, 360);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private static String resolveLocalIp() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            String firstNonLoopback = null;
            while (interfaces != null && interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                if (!networkInterface.isUp() || networkInterface.isLoopback() || networkInterface.isVirtual()) {
                    continue;
                }
                Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (!(address instanceof Inet4Address) || address.isLoopbackAddress()) {
                        continue;
                    }
                    if (address.isSiteLocalAddress()) {
                        return address.getHostAddress();
                    }
                    if (firstNonLoopback == null) {
                        firstNonLoopback = address.getHostAddress();
                    }
                }
            }
            if (firstNonLoopback != null) {
                return firstNonLoopback;
            }
        } catch (Exception ignored) {
            // Fallback below.
        }
        return "0.0.0.0";
    }
}
