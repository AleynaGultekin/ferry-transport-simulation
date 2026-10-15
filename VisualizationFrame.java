import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.GridLayout;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

public final class VisualizationFrame extends JFrame {
    private final JLabel ferrySideLabel = new JLabel();
    private final JLabel loadLabel = new JLabel();
    private final JLabel queueLabel = new JLabel();
    private final JLabel progressLabel = new JLabel();
    private final JLabel tripsLabel = new JLabel();

    private final JTextArea sideAText = createArea();
    private final JTextArea sideBText = createArea();
    private final JTextArea onboardText = createArea();
    private final JTextArea eventText = createArea();

    private VisualizationFrame() {
        super("Ferry Transport Simulation - Real Time Visualization");
        setSize(980, 720);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setLocationByPlatform(true);
        setLayout(new BorderLayout(12, 12));

        JPanel topPanel = new JPanel(new GridLayout(1, 5, 10, 10));
        topPanel.setBorder(BorderFactory.createEmptyBorder(12, 12, 0, 12));
        topPanel.add(createStatCard("Ferry Side", ferrySideLabel));
        topPanel.add(createStatCard("Current Load", loadLabel));
        topPanel.add(createStatCard("Queue Sizes", queueLabel));
        topPanel.add(createStatCard("Progress", progressLabel));
        topPanel.add(createStatCard("Trips", tripsLabel));

        JPanel centerPanel = new JPanel(new GridLayout(1, 3, 10, 10));
        centerPanel.setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 12));
        centerPanel.add(createPanel("Side A Queue", sideAText));
        centerPanel.add(createPanel("Onboard Vehicles", onboardText));
        centerPanel.add(createPanel("Side B Queue", sideBText));

        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(0, 12, 12, 12));
        bottomPanel.add(createPanel("Live Event Log", eventText), BorderLayout.CENTER);

        add(topPanel, BorderLayout.NORTH);
        add(centerPanel, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);
    }

    public static VisualizationFrame createIfAvailable() {
        if (GraphicsEnvironment.isHeadless()) {
            return null;
        }
        VisualizationFrame frame = new VisualizationFrame();
        SwingUtilities.invokeLater(() -> frame.setVisible(true));
        return frame;
    }

    public void updateSnapshot(VisualizationSnapshot snapshot) {
        SwingUtilities.invokeLater(() -> {
            ferrySideLabel.setText("Side " + snapshot.ferrySide());
            loadLabel.setText(snapshot.currentLoad() + " / " + snapshot.ferryCapacity());
            queueLabel.setText("A: " + snapshot.sideAQueueSize() + " | B: " + snapshot.sideBQueueSize());
            progressLabel.setText(snapshot.completedVehicles() + " / " + snapshot.totalVehicles());
            tripsLabel.setText(Long.toString(snapshot.totalTrips()));

            sideAText.setText(formatSidePanel(
                "A",
                snapshot.sideAWaitingForToll(),
                snapshot.sideAInToll(),
                snapshot.sideAQueuePreview(),
                snapshot.sideAQueueSize(),
                snapshot.ferrySide()
            ));
            sideBText.setText(formatSidePanel(
                "B",
                snapshot.sideBWaitingForToll(),
                snapshot.sideBInToll(),
                snapshot.sideBQueuePreview(),
                snapshot.sideBQueueSize(),
                snapshot.ferrySide()
            ));
            onboardText.setText(formatOnboard(snapshot.onboardVehicles(), snapshot.currentLoad(), snapshot.ferryCapacity(), snapshot.ferrySide()));
            setTitle("Ferry Transport Simulation - Real Time Visualization - " + snapshot.timeMs() + " ms");
        });
    }

    public void appendEvent(String line) {
        SwingUtilities.invokeLater(() -> {
            eventText.append(line + System.lineSeparator());
            eventText.setCaretPosition(eventText.getDocument().getLength());
        });
    }

    private JPanel createStatCard(String title, JLabel valueLabel) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(180, 190, 200)),
            BorderFactory.createEmptyBorder(10, 10, 10, 10)
        ));
        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
        valueLabel.setFont(new Font("SansSerif", Font.PLAIN, 18));
        panel.add(titleLabel, BorderLayout.NORTH);
        panel.add(valueLabel, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createPanel(String title, JTextArea area) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder(title));
        panel.add(new JScrollPane(area), BorderLayout.CENTER);
        return panel;
    }

    private JTextArea createArea() {
        JTextArea area = new JTextArea();
        area.setEditable(false);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setRows(10);
        return area;
    }

    private String formatSidePanel(
        String sideName,
        List<String> waitingForToll,
        List<String> inToll,
        List<String> queueItems,
        int queueSize,
        Side ferrySide
    ) {
        StringBuilder builder = new StringBuilder();
        builder.append("Waiting for toll").append(System.lineSeparator());
        builder.append(formatBlock(waitingForToll)).append(System.lineSeparator()).append(System.lineSeparator());

        builder.append("Currently in toll").append(System.lineSeparator());
        builder.append(formatBlock(inToll)).append(System.lineSeparator()).append(System.lineSeparator());

        builder.append("FIFO queue order").append(System.lineSeparator());
        if (queueItems.isEmpty()) {
            builder.append("No vehicle has joined the FIFO queue yet.");
        } else {
            builder.append("Queue size: ").append(queueSize).append(System.lineSeparator());
            builder.append(String.join(System.lineSeparator(), queueItems));
        }

        if (waitingForToll.isEmpty() && inToll.isEmpty() && queueItems.isEmpty()) {
            builder.append(System.lineSeparator()).append(System.lineSeparator());
            if (ferrySide.name().equals(sideName)) {
                builder.append("The ferry is currently on Side ").append(sideName).append(".");
            } else {
                builder.append("The ferry is currently on the opposite side.");
            }
        }
        return builder.toString();
    }

    private String formatOnboard(List<String> items, int currentLoad, int ferryCapacity, Side ferrySide) {
        if (items.isEmpty()) {
            return "The ferry is currently empty.\n"
                + "Current side: " + ferrySide + "\n"
                + "Load: " + currentLoad + " / " + ferryCapacity + "\n"
                + "Vehicle count: 0\n"
                + "Vehicles will be listed here while traveling.";
        }
        return "Onboard load: " + currentLoad + " / " + ferryCapacity + System.lineSeparator()
            + "Vehicle count: " + items.size() + System.lineSeparator()
            + System.lineSeparator()
            + String.join(System.lineSeparator(), items);
    }

    private String formatBlock(List<String> items) {
        if (items.isEmpty()) {
            return "-";
        }
        return String.join(System.lineSeparator(), items);
    }
}
