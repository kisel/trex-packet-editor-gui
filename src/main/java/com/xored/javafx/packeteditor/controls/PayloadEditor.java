package com.xored.javafx.packeteditor.controls;

import com.google.inject.Injector;
import com.xored.javafx.packeteditor.TRexPacketCraftingTool;
import javafx.beans.property.StringProperty;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import org.slf4j.LoggerFactory;

import static javafx.application.Platform.*;

public class PayloadEditor extends VBox {
    static org.slf4j.Logger logger = LoggerFactory.getLogger(TRexPacketCraftingTool.class);

    @FXML private VBox root;
    @FXML private HBox payloadEditorHboxChoice;
    @FXML private HBox payloadEditorHboxValue;

    // Choice
    @FXML
    private ChoiceBox<String> payloadChoiceType;
    
    @FXML private Button payloadButtonGo;
    @FXML private GridPane payloadEditorGrid;

    // Payload from text
    @FXML private TextArea textText;

    // Payload from file
    @FXML private TextField textFilename;
    @FXML private Button    textFilenameButton;

    // Payload generation from text pattern
    @FXML private TextField textPatternSize;
    @FXML private TextArea  textPatternText;

    // Payload generation from file pattern
    @FXML private TextField filePatternSize;
    @FXML private TextField filePatternFilename;
    @FXML private Button    filePatternButton;

    // Payload random ascii generation
    @FXML private TextField randomAsciiSize;

    // Payload random non-ascii generation
    @FXML private TextField randomNonAsciiSize;


    public enum PayloadType {
        UNKNOWN         (-1, "UNKNOWN"),
        TEXT            (0, "Text"),
        FILE            (1, "File"),
        TEXT_PATTERN    (2, "Text pattern"),
        FILE_PATTERN    (3, "File pattern"),
        RANDOM_ASCII    (4, "Random ASCII"),
        RANDOM_NON_ASCII(5, "Random non ASCII");

        private final int    index;
        private final String human;

        PayloadType(int index, String human) {
            this.index = index;
            this.human = human;
        }

        public int    index() { return index; }
        public String human() { return human; }
    }

    public enum EditorMode {
        UNKNOWN (-1, "UNKNOWN"),
        READ    (0, "Reading mode"),
        EDIT    (1, "Editing mode");

        private final int    mode;
        private final String human;

        EditorMode(int mode, String human) {
            this.mode = mode;
            this.human = human;
        }

        public int    mode()  { return mode; }
        public String human() { return human; }
    }

    private PayloadType type = PayloadType.UNKNOWN;
    private EditorMode mode = EditorMode.UNKNOWN;

    public PayloadEditor(Injector injector) {
        FXMLLoader fxmlLoader = injector.getInstance(FXMLLoader.class);

        fxmlLoader.setLocation(ClassLoader.getSystemResource("com/xored/javafx/packeteditor/controllers/PayloadEditor.fxml"));
        fxmlLoader.setRoot(this);
        fxmlLoader.setController(this);

        try {
            fxmlLoader.load();
        } catch (Exception e) {
            logger.error(e.getMessage());
        }

        payloadChoiceType.setOnAction((event) -> {
            int index = payloadChoiceType.getSelectionModel().getSelectedIndex();
            if (index >= 0) {
                payloadEditorHboxValue.setVisible(true);
                payloadEditorHboxValue.setManaged(true);
                runLater(() -> gridSetVisible(payloadEditorGrid, index));
                setType(int2type(index));
                setMode(EditorMode.EDIT);
            }
            else {
                setType(PayloadType.UNKNOWN);
                setMode(EditorMode.READ);
            }
        });
        setMode(EditorMode.EDIT);
    }

    public EditorMode getMode() {
        return mode;
    }

    public void setMode(EditorMode mode) {
        this.mode = mode;
        switch (mode) {
            case READ:
                payloadEditorHboxChoice.setVisible(false);
                payloadEditorHboxChoice.setManaged(false);
                payloadEditorHboxValue.setVisible(false);
                payloadEditorHboxValue.setManaged(false);
                break;
            case EDIT:
                payloadEditorHboxChoice.setVisible(true);
                payloadEditorHboxChoice.setManaged(true);

                int index = payloadChoiceType.getSelectionModel().getSelectedIndex();
                if (index >= 0) {
                    payloadEditorHboxValue.setVisible(true);
                    payloadEditorHboxValue.setManaged(true);
                } else {
                    payloadEditorHboxValue.setVisible(false);
                    payloadEditorHboxValue.setManaged(false);
                }
                break;
            default:
                logger.error("Unknown payload editor mode");
        }
    }

    public PayloadType getType() {
        return type;
    }

    public void setType(PayloadType type) {
        this.type = type;

        if (type != PayloadType.UNKNOWN) {
            select(type2int(type));
            setMode(EditorMode.EDIT);
        }
        else {
            getSelectionModel().select(false);
            payloadEditorHboxValue.setVisible(false);
            payloadEditorHboxValue.setManaged(false);
            setMode(EditorMode.READ);
        }
    }

    public String getText() {
        return textProperty().get();
    }

    public void setText(String value) {
        textProperty().set(value);
    }

    public StringProperty textProperty() {
        return textText.textProperty();
    }

    public void setPrefSize(double width, double height) {
        super.setPrefSize(width, height);
    }

    public void setContextMenu(ContextMenu contextMenu) {
        textText.setContextMenu(contextMenu);
    }

    public SingleSelectionModel getSelectionModel() {
        return payloadChoiceType.getSelectionModel();
    }

    public void select(int index) {
        runLater(() -> {
            gridSetVisible(payloadEditorGrid, index);
            getSelectionModel().select(index);
        });
    }

    public final void setOnAction(EventHandler<ActionEvent> value) {
        payloadButtonGo.setOnAction(value);
    }

    private void gridSetVisible(GridPane grid, int index) {
        for (Node node : grid.getChildren()) {
            node.setVisible(false);
            node.setManaged(false);
        }
        if (index >= 0) {
            Node node = grid.getChildren().get(index);
            node.setVisible(true);
            node.setManaged(true);

            double width = node.getLayoutBounds().getWidth();
            double height = node.getLayoutBounds().getHeight();
            Pane parentpane = (Pane) grid.getParent();
            parentpane.setMinSize(width, height);
            parentpane.setPrefSize(width, height);
            parentpane.setMaxSize(width, height);
        }
    }

    private int type2int(PayloadType type) {
        return type.index;
    }

    private PayloadType int2type(int index) {
        for (PayloadType t : PayloadType.values()) {
            if (t.index == index) return t;
        }
        return PayloadType.UNKNOWN;
    }
}
