package com.xored.javafx.packeteditor.controllers;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.xored.javafx.packeteditor.data.BinaryData;
import com.xored.javafx.packeteditor.data.OldField;
import com.xored.javafx.packeteditor.data.IBinaryData;
import com.xored.javafx.packeteditor.data.PacketDataController;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.util.Callback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.net.URL;
import java.util.*;

public class FieldEditorController implements Initializable, Observer {
    static Logger log = LoggerFactory.getLogger(FieldEditorController.class);
    @FXML private VBox fieldEditorBox;

    @Inject
    private IBinaryData binaryData;

    @Inject
    private PacketDataController packetController;

    TreeTableView<OldField> treeTableView;

    public TreeItem<OldField> buildTree() {
        List<TreeItem<OldField>> treeItems = new ArrayList<>();
        List<String> currentPath = new ArrayList<>();

        Iterator it = packetController.getProtocols().iterator();
        while(it.hasNext()) {
            int protocolLength = 0;
            JsonObject protocol = (JsonObject) it.next();
            String protocolId = protocol.get("id").getAsString();

            currentPath = new ArrayList<>(currentPath);
            currentPath.add(protocolId);

            JsonArray fields = protocol.getAsJsonArray("fields");
            Iterator fieldsIt = fields.iterator();
            List<TreeItem<OldField>> fieldItems = new ArrayList<>();
            Integer protocolOffset = protocol.get("offset").getAsInt();
            while (fieldsIt.hasNext()) {
                JsonObject field =(JsonObject) fieldsIt.next();
                String fieldId = field.get("id").getAsString();
                Integer offset = field.get("offset").getAsInt();
                Integer length = field.get("length").getAsInt();
                // field.get("value") this value can be string, numeric or potentially json object. it is a value from Scapy as is
                String value = field.get("hvalue").getAsString(); // human-representation of a Scapy value. similar to what we get with show2
                protocolLength += length;

                OldField fieldObj = new OldField(fieldId, offset, length, protocolOffset, value, OldField.Type.STRING);
                fieldObj.setPath(currentPath);
                fieldObj.setOnSetValue( newValue -> packetController.setFieldValue(fieldObj, newValue) );

                fieldItems.add(new TreeItem<>(fieldObj));
            }
            
            OldField protocolOldField = new OldField(protocolId, protocolOffset, protocolLength, 0, null, OldField.Type.PROTOCOL);
            TreeItem<OldField> protocolTreeItem = new TreeItem<>(protocolOldField);
            protocolTreeItem.setExpanded(true);
            protocolTreeItem.getChildren().addAll(fieldItems);
            treeItems.add(protocolTreeItem);
        }

        final TreeItem<OldField> root = new TreeItem<>(new OldField("Root", 0, 0, 0, null, OldField.Type.NONE));
        root.setExpanded(true);
        root.getChildren().addAll(treeItems);

        return root;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        packetController.addObserver(this);
        packetController.init();

        binaryData.getObservable().addObserver(this);
    }

    private void rebuildTreeView() {
        final TreeItem<OldField> root = buildTree();

        TreeTableColumn<OldField, String> dataColumn = new TreeTableColumn<>("OldField");
        dataColumn.setPrefWidth(150);
        dataColumn
                .setCellValueFactory((
                        TreeTableColumn.CellDataFeatures<OldField, String> param) -> new ReadOnlyStringWrapper(
                            param.getValue().getValue().getName()));

        TreeTableColumn<OldField, String> valueColumn = new TreeTableColumn<>(
                "Value");
        valueColumn.setPrefWidth(190);

        Callback<TreeTableColumn<OldField, String>, TreeTableCell<OldField, String>> cellFactory
                = (TreeTableColumn<OldField, String> param) -> new EditingCell();

        valueColumn.setCellFactory(cellFactory);
        valueColumn
                .setCellValueFactory(
                        (TreeTableColumn.CellDataFeatures<OldField, String> param) -> {
                            OldField oldField = param.getValue().getValue();
                            if (oldField.getName().startsWith("Ethernet") || oldField.getName().startsWith("IPv4")) {
                                return new ReadOnlyStringWrapper("");
                            }
                            return new SimpleStringProperty(oldField.getDisplayValue());
                        });

        valueColumn.setOnEditCommit(
                (TreeTableColumn.CellEditEvent<OldField, String> t) -> {
                    OldField f = (OldField) t.getTreeTableView().getTreeItem(t.getTreeTablePosition().getRow()).getValue();
                    String newValue = t.getNewValue();
                    f.setStringValue(newValue);
                });

        treeTableView = new TreeTableView<>(root);
        treeTableView.getColumns().setAll(dataColumn, valueColumn);

        treeTableView.getSelectionModel().selectedItemProperty().addListener(new ChangeListener() {

            @Override
            public void changed(ObservableValue observable, Object oldValue,
                                Object newValue) {

                TreeItem<OldField> selectedItem = (TreeItem<OldField>) newValue;
                binaryData.setSelected(selectedItem.getValue().getAbsOffset(), selectedItem.getValue().getLength());
            }

        });

        treeTableView.setEditable(true);
        treeTableView.setShowRoot(false);

        fieldEditorBox.getChildren().clear();

        // TODO: move to FXML
        HBox buttons = new HBox();
        Button loadpcapBtn = new Button("Load pcap");
        loadpcapBtn.setId("loadpcapBtn");
        loadpcapBtn.setOnAction(e -> this.loadPcapDlg());
        buttons.getChildren().add(loadpcapBtn);

        Button savePcapBtn = new Button("Save pcap");
        savePcapBtn.setId("savepcapBtn");
        savePcapBtn.setOnAction(e -> this.savePcapDlg());
        buttons.getChildren().add(savePcapBtn);

        fieldEditorBox.getChildren().add(buttons);
        fieldEditorBox.getChildren().add(treeTableView);
        fieldEditorBox.setVgrow(treeTableView, Priority.ALWAYS);
    }

    void showError(String title, Exception e) {
        log.error("{}: {}", title, e);
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setHeaderText(title);
        alert.initOwner(fieldEditorBox.getScene().getWindow());
        alert.getDialogPane().setExpandableContent(new ScrollPane(new TextArea(title + ": " + e.getMessage())));
        alert.showAndWait();
    }

    void loadPcapDlg() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Open Pcap File");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Pcap Files", "*.pcap", "*.cap"),
                new FileChooser.ExtensionFilter("All Files", "*.*"));
        java.io.File pcapfile = fileChooser.showOpenDialog(fieldEditorBox.getScene().getWindow());
        if (pcapfile != null) {
            try {
                packetController.loadPcapFile(pcapfile);
            } catch (Exception e) {
                showError("Failed to load pcap file", e);
            }
        }
    }

    void savePcapDlg() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save to Pcap File");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Pcap Files", "*.pcap", "*.cap"),
                new FileChooser.ExtensionFilter("All Files", "*.*"));
        java.io.File pcapfile = fileChooser.showSaveDialog(fieldEditorBox.getScene().getWindow());
        if (pcapfile != null) {
            try {
                packetController.writeToPcapFile(pcapfile);
            } catch (Exception e) {
                showError("Failed to save pcap file", e);
            }
        }
    }

    /*
    private String getFieldStringValue(OldField oldField) {
        byte[] bytes = binaryData.getBytes(oldField.getAbsOffset(), oldField.getLength());
        String result = "";
        if (oldField.getType() == OldField.Type.BINARY) {
            int value = 0;
            for (int i = 0; i < bytes.length; i++) {
                value |= (int) (bytes[bytes.length - 1 - i] & 0xFF) << i * 8;
            }

            return String.format("%d", value);
        } else if (oldField.getType() == OldField.Type.MAC_ADDRESS) {
            return String.format("%02X:%02X:%02X:%02X:%02X:%02X", bytes[0], bytes[1], bytes[2], bytes[3], bytes[4], bytes[5]);
        } else if (oldField.getType() == OldField.Type.IP_ADDRESS) {
            return String.format("%d.%d.%d.%d", (0x000000FF & (int)bytes[0]), (0x000000FF & (int)bytes[1]), (0x000000FF & (int)bytes[2]), (0x000000FF & (int)bytes[3]));
        }
        return result;
        return oldField.getDisplayValue();
    }

    private byte[] parseFieldBytes(OldField oldField, String value) {
        byte[] bytes = new byte[0];
        if (oldField.getType() == OldField.Type.BINARY) {
            if (oldField.getLength() == 4) {
                bytes = ByteBuffer.allocate(4).putInt(Integer.parseInt(value)).array();
            } else {
                bytes = ByteBuffer.allocate(2).putShort(Short.parseShort(value)).array();
            }
        } else if (oldField.getType() == OldField.Type.MAC_ADDRESS) {
            try {
                ByteBuffer bf = ByteBuffer.allocate(6);
                String[] parts = value.split(":");

                bf.put((byte) (Integer.parseInt(parts[0], 16)));
                bf.put((byte) (Integer.parseInt(parts[1], 16)));
                bf.put((byte) (Integer.parseInt(parts[2], 16)));
                bf.put((byte) (Integer.parseInt(parts[3], 16)));
                bf.put((byte) (Integer.parseInt(parts[4], 16)));
                bf.put((byte) (Integer.parseInt(parts[5], 16)));

                bytes = bf.array();
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else if (oldField.getType() == OldField.Type.IP_ADDRESS) {
            try {
                ByteBuffer bf = ByteBuffer.allocate(4);
                String[] parts = value.split("\\.");

                bf.put((byte) (Integer.parseInt(parts[0])));
                bf.put((byte) (Integer.parseInt(parts[1])));
                bf.put((byte) (Integer.parseInt(parts[2])));
                bf.put((byte) (Integer.parseInt(parts[3])));

                bytes = bf.array();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return bytes;
    }
    */

    @Override
    public void update(Observable o, Object arg) {
        if ((o == binaryData) && (BinaryData.OP.SET_BYTE.equals(arg))) {
            treeTableView.refresh();
        } else if (o == packetController) {
            rebuildTreeView();
        }
    }


    class EditingCell extends TreeTableCell<OldField, String> {

        private TextField textField;

        private EditingCell() {
        }

        @Override
        public void startEdit() {
            if (!isEmpty()) {
                super.startEdit();
                createTextField();
                setText(null);
                setGraphic(textField);
                textField.selectAll();
            }
        }

        @Override
        public void cancelEdit() {
            super.cancelEdit();

            setText((String) getItem());
            setGraphic(null);
        }

        @Override
        public void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);

            if (empty) {
                setText(item);
                setGraphic(null);
            } else {
                if (isEditing()) {
                    if (textField != null) {
                        textField.setText(getString());
//                        setGraphic(null);
                    }
                    setText(null);
                    setGraphic(textField);
                } else {
                    setText(getString());
                    setGraphic(null);
                }
            }
        }

        private void createTextField() {
            textField = new TextField(getString());
            textField.setMinWidth(this.getWidth() - this.getGraphicTextGap() * 2);
            textField.setOnAction((e) -> commitEdit(textField.getText()));
            textField.focusedProperty().addListener((ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) -> {
                if (!newValue) {
                    System.out.println("Commiting " + textField.getText());
                    commitEdit(textField.getText());
                }
            });
        }

        private String getString() {
            return getItem() == null ? "" : getItem();
        }
    }

}