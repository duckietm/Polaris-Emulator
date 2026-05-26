package com.eu.habbo.gui;

import com.eu.habbo.Emulator;
import com.eu.habbo.monitoring.EmulatorStatsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class EmulatorDashboard extends JFrame {
    private static final Logger LOGGER = LoggerFactory.getLogger(EmulatorDashboard.class);

    // Modern Dark Theme Colors
    private static final Color COLOR_BG = new Color(18, 18, 18);
    private static final Color COLOR_SURFACE = new Color(30, 30, 30);
    private static final Color COLOR_SURFACE_HOVER = new Color(45, 45, 45);
    private static final Color COLOR_PRIMARY = new Color(99, 102, 241);
    private static final Color COLOR_PRIMARY_SOFT = new Color(99, 102, 241, 45);
    private static final Color COLOR_SUCCESS = new Color(34, 197, 94);
    private static final Color COLOR_WARNING = new Color(245, 158, 11);
    private static final Color COLOR_TEXT = new Color(240, 240, 240);
    private static final Color COLOR_TEXT_MUTED = new Color(150, 150, 150);
    private static final Color COLOR_TEXT_SUBTLE = new Color(110, 110, 110);
    private static final Color COLOR_BORDER = new Color(50, 50, 50);
    private static final Font FONT_TITLE = new Font("Segoe UI", Font.BOLD, 26);
    private static final Font FONT_SECTION = new Font("Segoe UI", Font.BOLD, 16);
    private static final Font FONT_SMALL = new Font("Segoe UI", Font.PLAIN, 12);
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private static EmulatorDashboard instance;

    // Overview Tab
    private final JLabel memLabel = createMetricLabel();
    private final JLabel cpuLabel = createMetricLabel();
    private final JLabel threadLabel = createMetricLabel();
    private final JLabel usersLabel = createMetricLabel();
    private final JLabel roomsLabel = createMetricLabel();
    private final JLabel wiredLabel = createMetricLabel();
    private final JLabel uptimeLabel = createStatusValueLabel();
    private final JLabel lastUpdatedLabel = createStatusValueLabel();
    private final JLabel footerStatusLabel = createStatusValueLabel();
    private final MemoryGraphPanel memoryGraph = new MemoryGraphPanel();

    // Tables
    private final DefaultTableModel usersTableModel;
    private final DefaultTableModel roomsTableModel;
    private final DefaultTableModel wiredTableModel;
    private final JTable usersTable;
    private final JTable roomsTable;
    private final JTable wiredTable;
    private final JLabel usersCountLabel = createCountLabel();
    private final JLabel roomsCountLabel = createCountLabel();
    private final JLabel wiredCountLabel = createCountLabel();

    // UI Components
    private final JPanel cardsPanel;
    private final CardLayout cardLayout;
    private final Map<String, JPanel> navButtons = new HashMap<>();
    private String selectedCardName = "Overview";

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "Dashboard-Updater");
        t.setDaemon(true);
        return t;
    });

    private EmulatorDashboard() {
        setTitle("Arcturus Morningstar - System Dashboard");
        setSize(1100, 700);
        setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        setLocationRelativeTo(null);
        getContentPane().setBackground(COLOR_BG);
        setLayout(new BorderLayout());

        // Setup custom Look & Feel basics to remove weird Swing borders
        UIManager.put("ScrollBar.background", COLOR_BG);
        UIManager.put("ScrollBar.thumb", COLOR_SURFACE_HOVER);

        // Sidebar
        JPanel sidebar = new JPanel();
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setBackground(COLOR_SURFACE);
        sidebar.setPreferredSize(new Dimension(220, 0));
        sidebar.setBorder(new MatteBorder(0, 0, 0, 1, COLOR_BORDER));

        // Sidebar Header
        JPanel brandPanel = new JPanel(new BorderLayout());
        brandPanel.setBackground(COLOR_SURFACE);
        brandPanel.setBorder(new EmptyBorder(20, 20, 30, 20));
        
        JLabel brandTitle = new JLabel("Arcturus");
        brandTitle.setFont(new Font("Segoe UI", Font.BOLD, 22));
        brandTitle.setForeground(COLOR_TEXT);
        
        JLabel brandSub = new JLabel("v" + Emulator.version);
        brandSub.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        brandSub.setForeground(COLOR_PRIMARY);

        brandPanel.add(brandTitle, BorderLayout.NORTH);
        brandPanel.add(brandSub, BorderLayout.SOUTH);
        sidebar.add(brandPanel);

        // Main Cards
        cardLayout = new CardLayout();
        cardsPanel = new JPanel(cardLayout);
        cardsPanel.setBackground(COLOR_BG);

        // Setup Tabs
        usersTableModel = createTableModel(new String[]{"ID", "Username", "Rank", "Credits", "Room ID"});
        roomsTableModel = createTableModel(new String[]{"Room ID", "Name", "Players", "Items", "Tickables", "CPU (ms)", "Est. RAM (KB)", "Thread"});
        wiredTableModel = createTableModel(new String[]{"Room ID", "Avg Tick", "Peak Tick", "Usage %", "Delayed", "Overloaded?", "Heavy?"});
        usersTable = createDashboardTable(usersTableModel);
        roomsTable = createDashboardTable(roomsTableModel);
        wiredTable = createDashboardTable(wiredTableModel);

        cardsPanel.add(createOverviewTab(), "Overview");
        cardsPanel.add(createTableTab("Online Users", "Players currently connected to the emulator.", usersTable, usersCountLabel), "Online Users");
        cardsPanel.add(createTableTab("Active Rooms", "Loaded rooms with lightweight performance indicators.", roomsTable, roomsCountLabel), "Active Rooms");
        cardsPanel.add(createTableTab("Wired Diagnostics", "Rooms currently using wired timing, delay and execution budget.", wiredTable, wiredCountLabel), "Wired Diagnostics");

        // Sidebar Navigation
        sidebar.add(createNavButton("Overview", "Overview"));
        sidebar.add(Box.createRigidArea(new Dimension(0, 10)));
        sidebar.add(createNavButton("Online Users", "Online Users"));
        sidebar.add(Box.createRigidArea(new Dimension(0, 10)));
        sidebar.add(createNavButton("Active Rooms", "Active Rooms"));
        sidebar.add(Box.createRigidArea(new Dimension(0, 10)));
        sidebar.add(createNavButton("Wired Diagnostics", "Wired Diagnostics"));
        sidebar.add(Box.createVerticalGlue());

        add(sidebar, BorderLayout.WEST);
        add(cardsPanel, BorderLayout.CENTER);
        add(createStatusBar(), BorderLayout.SOUTH);

        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentShown(ComponentEvent e) {
                setActiveCard("Overview");
            }
        });

        // Start updates
        scheduler.scheduleAtFixedRate(this::updateMetrics, 0, 1, TimeUnit.SECONDS);
    }

    private DefaultTableModel createTableModel(String[] cols) {
        return new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
    }

    private JPanel createNavButton(String text, String cardName) {
        JPanel btn = new JPanel(new BorderLayout());
        btn.setBackground(COLOR_SURFACE);
        btn.setMaximumSize(new Dimension(220, 45));
        btn.setBorder(new EmptyBorder(0, 18, 0, 0));
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        navButtons.put(cardName, btn);

        JLabel lbl = new JLabel(text);
        lbl.setFont(new Font("Segoe UI", Font.BOLD, 14));
        lbl.setForeground(COLOR_TEXT_MUTED);
        btn.add(lbl, BorderLayout.CENTER);

        btn.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                btn.setBackground(COLOR_SURFACE_HOVER);
                lbl.setForeground(COLOR_TEXT);
            }
            @Override
            public void mouseExited(MouseEvent e) {
                updateNavButtonStyle(cardName, btn, lbl);
            }
            @Override
            public void mouseClicked(MouseEvent e) {
                setActiveCard(cardName);
            }
        });

        updateNavButtonStyle(cardName, btn, lbl);
        return btn;
    }

    private JPanel createOverviewTab() {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(COLOR_BG);
        wrapper.setBorder(new EmptyBorder(30, 30, 30, 30));

        JPanel header = new JPanel(new BorderLayout(0, 14));
        header.setOpaque(false);

        JLabel title = new JLabel("Dashboard Overview");
        title.setFont(FONT_TITLE);
        title.setForeground(COLOR_TEXT);

        JLabel subtitle = new JLabel("Operational view of emulator health, activity and wired performance.");
        subtitle.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        subtitle.setForeground(COLOR_TEXT_MUTED);

        JPanel titleBlock = new JPanel();
        titleBlock.setOpaque(false);
        titleBlock.setLayout(new BoxLayout(titleBlock, BoxLayout.Y_AXIS));
        titleBlock.add(title);
        titleBlock.add(Box.createRigidArea(new Dimension(0, 4)));
        titleBlock.add(subtitle);

        header.add(titleBlock, BorderLayout.NORTH);
        header.add(createOverviewMetaPanel(), BorderLayout.SOUTH);
        wrapper.add(header, BorderLayout.NORTH);

        JPanel content = new JPanel(new GridLayout(1, 2, 20, 20));
        content.setOpaque(false);
        content.setBorder(new EmptyBorder(20, 0, 0, 0));

        // Left Stats
        JPanel statsPanel = new JPanel(new GridLayout(3, 2, 12, 12));
        statsPanel.setOpaque(false);
        statsPanel.add(createMetricCard("Memory Allocation", memLabel));
        statsPanel.add(createMetricCard("CPU Load", cpuLabel));
        statsPanel.add(createMetricCard("Active OS Threads", threadLabel));
        statsPanel.add(createMetricCard("Connected Players", usersLabel));
        statsPanel.add(createMetricCard("Loaded Rooms", roomsLabel));
        statsPanel.add(createMetricCard("Wired Tickables", wiredLabel));
        content.add(statsPanel);

        // Right Graph
        JPanel graphContainer = new JPanel(new BorderLayout());
        graphContainer.setBackground(COLOR_SURFACE);
        graphContainer.setBorder(BorderFactory.createCompoundBorder(
                new MatteBorder(1, 1, 1, 1, COLOR_BORDER),
                new EmptyBorder(15, 15, 15, 15)
        ));
        
        JLabel gTitle = new JLabel("Realtime Memory Usage");
        gTitle.setFont(FONT_SECTION);
        gTitle.setForeground(COLOR_TEXT_MUTED);
        gTitle.setBorder(new EmptyBorder(0, 0, 15, 0));
        graphContainer.add(gTitle, BorderLayout.NORTH);
        graphContainer.add(memoryGraph, BorderLayout.CENTER);
        content.add(graphContainer);

        wrapper.add(content, BorderLayout.CENTER);
        return wrapper;
    }

    private JPanel createMetricCard(String title, JLabel valueLabel) {
        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(COLOR_SURFACE);
        card.setBorder(BorderFactory.createCompoundBorder(
                new MatteBorder(1, 1, 1, 1, COLOR_BORDER),
                new EmptyBorder(15, 20, 15, 20)
        ));

        JLabel tLabel = new JLabel(title);
        tLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
        tLabel.setForeground(COLOR_TEXT_MUTED);
        
        card.add(tLabel, BorderLayout.NORTH);
        card.add(valueLabel, BorderLayout.SOUTH);
        return card;
    }

    private JLabel createMetricLabel() {
        JLabel label = new JLabel("-");
        label.setFont(new Font("Segoe UI", Font.BOLD, 28));
        label.setForeground(COLOR_TEXT);
        return label;
    }

    private JPanel createOverviewMetaPanel() {
        JPanel panel = new JPanel(new GridLayout(1, 3, 12, 12));
        panel.setOpaque(false);
        panel.add(createStatusCard("Uptime", uptimeLabel, COLOR_PRIMARY));
        panel.add(createStatusCard("Last Refresh", lastUpdatedLabel, COLOR_SUCCESS));
        panel.add(createStatusCard("GUI Status", footerStatusLabel, COLOR_WARNING));
        return panel;
    }

    private JPanel createStatusCard(String title, JLabel valueLabel, Color accent) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(COLOR_SURFACE);
        panel.setBorder(BorderFactory.createCompoundBorder(
                new MatteBorder(1, 1, 1, 1, COLOR_BORDER),
                new EmptyBorder(12, 14, 12, 14)
        ));

        JPanel accentBar = new JPanel();
        accentBar.setBackground(accent);
        accentBar.setPreferredSize(new Dimension(6, 0));

        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        JLabel label = new JLabel(title);
        label.setFont(FONT_SMALL);
        label.setForeground(COLOR_TEXT_MUTED);

        content.add(label);
        content.add(Box.createRigidArea(new Dimension(0, 6)));
        content.add(valueLabel);

        panel.add(accentBar, BorderLayout.WEST);
        panel.add(content, BorderLayout.CENTER);
        return panel;
    }

    private JLabel createStatusValueLabel() {
        JLabel label = new JLabel("-");
        label.setFont(new Font("Segoe UI", Font.BOLD, 16));
        label.setForeground(COLOR_TEXT);
        return label;
    }

    private JLabel createCountLabel() {
        JLabel label = new JLabel("0 rows");
        label.setFont(FONT_SMALL);
        label.setForeground(COLOR_TEXT_MUTED);
        return label;
    }

    private JTable createDashboardTable(DefaultTableModel model) {
        JTable table = new JTable(model);
        table.setBackground(COLOR_SURFACE);
        table.setForeground(COLOR_TEXT);
        table.setGridColor(COLOR_BORDER);
        table.setRowHeight(34);
        table.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        table.setFillsViewportHeight(true);
        table.setSelectionBackground(COLOR_PRIMARY);
        table.setSelectionForeground(Color.WHITE);
        table.setShowVerticalLines(false);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoCreateRowSorter(true);

        JTableHeader header = table.getTableHeader();
        header.setBackground(new Color(22, 22, 22));
        header.setForeground(COLOR_TEXT_MUTED);
        header.setFont(new Font("Segoe UI", Font.BOLD, 13));
        header.setPreferredSize(new Dimension(0, 38));
        header.setBorder(BorderFactory.createMatteBorder(1, 0, 1, 0, COLOR_BORDER));
        ((DefaultTableCellRenderer) header.getDefaultRenderer()).setHorizontalAlignment(JLabel.LEFT);
        ((DefaultTableCellRenderer) header.getDefaultRenderer()).setBorder(new EmptyBorder(0, 10, 0, 0));

        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                JLabel label = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                label.setBorder(new EmptyBorder(0, 10, 0, 10));
                label.setForeground(isSelected ? Color.WHITE : COLOR_TEXT);
                label.setBackground(isSelected ? COLOR_PRIMARY : ((row % 2 == 0) ? COLOR_SURFACE : new Color(35, 35, 35)));
                return label;
            }
        });

        return table;
    }

    private JPanel createTableTab(String title, String subtitle, JTable table, JLabel countLabel) {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(COLOR_BG);
        wrapper.setBorder(new EmptyBorder(30, 30, 30, 30));

        JPanel titlePanel = new JPanel(new BorderLayout());
        titlePanel.setOpaque(false);

        JLabel titleLbl = new JLabel(title);
        titleLbl.setFont(FONT_TITLE);
        titleLbl.setForeground(COLOR_TEXT);

        JLabel subtitleLbl = new JLabel(subtitle);
        subtitleLbl.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        subtitleLbl.setForeground(COLOR_TEXT_MUTED);
        subtitleLbl.setBorder(new EmptyBorder(6, 0, 0, 0));

        JPanel textPanel = new JPanel();
        textPanel.setOpaque(false);
        textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
        textPanel.add(titleLbl);
        textPanel.add(subtitleLbl);

        titlePanel.add(textPanel, BorderLayout.WEST);
        titlePanel.add(countLabel, BorderLayout.EAST);
        wrapper.add(titlePanel, BorderLayout.NORTH);

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.getViewport().setBackground(COLOR_SURFACE);
        scrollPane.setBorder(new MatteBorder(1, 1, 1, 1, COLOR_BORDER));
        scrollPane.setBorder(new CompoundBorder(
                new EmptyBorder(20, 0, 0, 0),
                new MatteBorder(1, 1, 1, 1, COLOR_BORDER)
        ));
        wrapper.add(scrollPane, BorderLayout.CENTER);
        
        return wrapper;
    }

    private JPanel createStatusBar() {
        JPanel statusBar = new JPanel(new BorderLayout());
        statusBar.setBackground(COLOR_SURFACE);
        statusBar.setBorder(new CompoundBorder(
                new MatteBorder(1, 0, 0, 0, COLOR_BORDER),
                new EmptyBorder(8, 14, 8, 14)
        ));

        JLabel left = new JLabel("Dashboard running locally");
        left.setFont(FONT_SMALL);
        left.setForeground(COLOR_TEXT_SUBTLE);

        JLabel right = new JLabel("Tip: table columns are sortable");
        right.setFont(FONT_SMALL);
        right.setForeground(COLOR_TEXT_SUBTLE);

        statusBar.add(left, BorderLayout.WEST);
        statusBar.add(right, BorderLayout.EAST);
        return statusBar;
    }

    private void setActiveCard(String cardName) {
        selectedCardName = cardName;
        cardLayout.show(cardsPanel, cardName);
        navButtons.forEach((name, button) -> {
            JLabel label = (JLabel) button.getComponent(0);
            updateNavButtonStyle(name, button, label);
        });
    }

    private void updateNavButtonStyle(String cardName, JPanel button, JLabel label) {
        boolean active = cardName.equals(selectedCardName);
        button.setBackground(active ? COLOR_PRIMARY_SOFT : COLOR_SURFACE);
        label.setForeground(active ? COLOR_TEXT : COLOR_TEXT_MUTED);
    }

    private void updateMetrics() {
        try {
            EmulatorStatsService.Snapshot snapshot = EmulatorStatsService.collectSnapshot();
            EmulatorStatsService.Overview overview = snapshot.overview;

            Object[][] usersData = new Object[snapshot.users.size()][5];
            for (int i = 0; i < snapshot.users.size(); i++) {
                EmulatorStatsService.OnlineUserRow user = snapshot.users.get(i);
                usersData[i] = new Object[]{user.id, user.username, user.rank, user.credits, user.roomId};
            }

            Object[][] roomsData = new Object[snapshot.rooms.size()][8];
            for (int i = 0; i < snapshot.rooms.size(); i++) {
                EmulatorStatsService.ActiveRoomRow room = snapshot.rooms.get(i);
                roomsData[i] = new Object[]{
                        room.roomId,
                        room.name,
                        room.players,
                        room.items,
                        room.tickables,
                        String.format("%.2f", room.cpuMs),
                        room.estimatedRamKb,
                        room.thread
                };
            }

            Object[][] wiredData = new Object[snapshot.wired.size()][7];
            for (int i = 0; i < snapshot.wired.size(); i++) {
                EmulatorStatsService.WiredRoomRow wiredRoom = snapshot.wired.get(i);
                wiredData[i] = new Object[]{
                        wiredRoom.roomId,
                        wiredRoom.averageTickMs + " ms",
                        wiredRoom.peakTickMs + " ms",
                        wiredRoom.usagePercent + "%",
                        wiredRoom.delayedEventsPending,
                        wiredRoom.overloaded ? "YES" : "NO",
                        wiredRoom.heavy ? "YES" : "NO"
                };
            }

            SwingUtilities.invokeLater(() -> {
                memLabel.setText(String.format("%d MB / %d MB", overview.memoryUsedMb, overview.memoryMaxMb));
                cpuLabel.setText(String.format("%.1f %%", overview.cpuLoadPercent));
                threadLabel.setText(String.valueOf(overview.activeOsThreads));
                usersLabel.setText(String.valueOf(overview.connectedPlayers));
                roomsLabel.setText(String.valueOf(overview.loadedRooms));
                wiredLabel.setText(String.valueOf(overview.wiredTickables));
                uptimeLabel.setText(EmulatorStatsService.formatDuration(overview.uptimeSeconds));
                lastUpdatedLabel.setText(LocalDateTime.now().format(TIME_FORMAT));
                footerStatusLabel.setText(overview.guiStatus);
                memoryGraph.addValue((long) overview.memoryUsedMb * 1024L * 1024L, (long) overview.memoryMaxMb * 1024L * 1024L);

                usersTableModel.setDataVector(usersData, new String[]{"ID", "Username", "Rank", "Credits", "Room ID"});
                roomsTableModel.setDataVector(roomsData, new String[]{"Room ID", "Name", "Players", "Items", "Tickables", "CPU (ms)", "Est. RAM (KB)", "Thread"});
                wiredTableModel.setDataVector(wiredData, new String[]{"Room ID", "Avg Tick", "Peak Tick", "Usage %", "Delayed", "Overloaded?", "Heavy?"});
                usersCountLabel.setText(snapshot.users.size() + " rows");
                roomsCountLabel.setText(snapshot.rooms.size() + " rows");
                wiredCountLabel.setText(snapshot.wired.size() + " rows");
            });
        } catch (Exception e) {
            LOGGER.error("Error updating dashboard metrics", e);
        }
    }

    public static void launch() {
        if (instance == null) {
            instance = new EmulatorDashboard();
        }
        SwingUtilities.invokeLater(() -> {
            instance.setVisible(true);
        });
    }

    private static class MemoryGraphPanel extends JPanel {
        private final LinkedList<Double> history = new LinkedList<>();
        private static final int MAX_POINTS = 100;

        public MemoryGraphPanel() {
            setOpaque(false);
        }

        public void addValue(long used, long max) {
            double percent = (double) used / (double) max;
            history.addLast(percent);
            if (history.size() > MAX_POINTS) {
                history.removeFirst();
            }
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int width = getWidth();
            int height = getHeight();

            // Background grid and labels
            g2.setFont(new Font("Segoe UI", Font.PLAIN, 10));
            long maxMemRaw = Runtime.getRuntime().maxMemory();
            
            for(int i = 0; i <= 4; i++) {
                int y = i == 0 ? 15 : height * i / 4;
                if (i == 4) y = height - 5;
                
                g2.setColor(COLOR_BORDER);
                g2.drawLine(0, y, width, y);
                
                // Draw Y-axis numbers
                g2.setColor(COLOR_TEXT_MUTED);
                long labelVal = (long) (maxMemRaw * (1.0 - (double)i / 4.0)) / 1024 / 1024;
                g2.drawString(labelVal + " MB", 5, y - 5);
            }

            if (history.size() < 2) return;

            double dx = (double) width / (MAX_POINTS - 1);
            Path2D path = new Path2D.Double();
            path.moveTo(0, height);

            int i = MAX_POINTS - history.size();
            for (Double val : history) {
                double x = i * dx;
                double y = height - (val * height);
                if (i == MAX_POINTS - history.size()) {
                    path.moveTo(x, y);
                } else {
                    path.lineTo(x, y);
                }
                i++;
            }

            // Draw line
            g2.setStroke(new BasicStroke(3.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(COLOR_PRIMARY);
            g2.draw(path);
            
            // Fill area
            path.lineTo(width, height);
            path.lineTo((MAX_POINTS - history.size()) * dx, height);
            path.closePath();
            
            GradientPaint fillPaint = new GradientPaint(
                0, 0, new Color(COLOR_PRIMARY.getRed(), COLOR_PRIMARY.getGreen(), COLOR_PRIMARY.getBlue(), 120),
                0, height, new Color(COLOR_PRIMARY.getRed(), COLOR_PRIMARY.getGreen(), COLOR_PRIMARY.getBlue(), 10)
            );
            g2.setPaint(fillPaint);
            g2.fill(path);

            Double lastValue = history.peekLast();
            if (lastValue != null) {
                String usageLabel = String.format("Usage %.1f%%", lastValue * 100.0);
                g2.setFont(new Font("Segoe UI", Font.BOLD, 12));
                FontMetrics metrics = g2.getFontMetrics();
                int labelWidth = metrics.stringWidth(usageLabel) + 16;
                int labelHeight = 24;
                int labelX = Math.max(8, width - labelWidth - 8);
                int labelY = 8;

                g2.setColor(new Color(0, 0, 0, 130));
                g2.fillRoundRect(labelX, labelY, labelWidth, labelHeight, 12, 12);
                g2.setColor(COLOR_TEXT);
                g2.drawString(usageLabel, labelX + 8, labelY + 16);
            }
        }
    }

    private static String formatDuration(long millis) {
        long totalSeconds = Math.max(0L, millis / 1000L);
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;
        return String.format("%02dh %02dm %02ds", hours, minutes, seconds);
    }
}
