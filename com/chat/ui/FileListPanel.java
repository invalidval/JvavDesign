package com.chat.ui;

import com.chat.file.FileRecord;
import com.chat.file.FileTransferManager;
import com.chat.file.FileTransferListener;
import com.chat.file.FileHistoryXmlManager;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableCellEditor;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.List;

public class FileListPanel extends JPanel {
    private JTable fileTable;
    private FileTableModel fileTableModel;
    private String currentUser;
    private boolean isGroup;
    private String targetId;
    private JFrame parentFrame;
    private com.chat.client.Client client;

    public FileListPanel(com.chat.client.Client client, String currentUser, boolean isGroup, String targetId, JFrame parentFrame) {
        this.client = client;
        this.currentUser = currentUser;
        this.isGroup = isGroup;
        this.targetId = targetId;
        this.parentFrame = parentFrame;
        setLayout(new BorderLayout());
        fileTableModel = new FileTableModel(FileHistoryXmlManager.loadHistory(currentUser, isGroup, targetId));
        fileTable = new JTable(fileTableModel);
        fileTable.getColumnModel().getColumn(0).setPreferredWidth(180);
        fileTable.getColumnModel().getColumn(1).setPreferredWidth(60);
        fileTable.getColumnModel().getColumn(2).setPreferredWidth(80);
        fileTable.getColumnModel().getColumn(3).setPreferredWidth(120);
        fileTable.getColumnModel().getColumn(3).setCellRenderer(new ButtonRenderer());
        fileTable.getColumnModel().getColumn(3).setCellEditor(new ButtonEditor(new JCheckBox()));
        JScrollPane fileScroll = new JScrollPane(fileTable);
        add(fileScroll, BorderLayout.CENTER);
        JButton refreshBtn = new JButton("刷新");
        refreshBtn.addActionListener(e -> refreshFileTable());
        add(refreshBtn, BorderLayout.SOUTH);
    }

    public void refreshFileTable() {
        fileTableModel.setData(FileHistoryXmlManager.loadHistory(currentUser, isGroup, targetId));
    }

    class FileTableModel extends AbstractTableModel {
        private String[] columns = {"文件名", "大小", "发送者", "操作"};
        private List<FileRecord> data;
        public FileTableModel(List<FileRecord> data) {
            this.data = data;
        }
        public void setData(List<FileRecord> data) {
            this.data = data;
            fireTableDataChanged();
        }
        @Override
        public int getRowCount() { return data == null ? 0 : data.size(); }
        @Override
        public int getColumnCount() { return columns.length; }
        @Override
        public String getColumnName(int col) { return columns[col]; }
        @Override
        public Object getValueAt(int row, int col) {
            FileRecord rec = data.get(row);
            switch (col) {
                case 0: return rec.fileName;
                case 1: return rec.fileSize / 1024 + " KB";
                case 2: return rec.sender;
                case 3: return "下载/删除";
            }
            return null;
        }
        @Override
        public boolean isCellEditable(int row, int col) { return col == 3; }
        @Override
        public Class<?> getColumnClass(int col) { return String.class; }
        public FileRecord getFileRecord(int row) { return data.get(row); }
    }

    // 渲染器
    class ButtonRenderer extends JPanel implements TableCellRenderer {
        private JButton downloadBtn = new JButton("下载");
        private JButton deleteBtn = new JButton("删除");
        public ButtonRenderer() {
            setLayout(new FlowLayout(FlowLayout.LEFT, 2, 0));
            add(downloadBtn);
            add(deleteBtn);
            setOpaque(true);
        }
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            return this;
        }
    }

    // 编辑器
    class ButtonEditor extends AbstractCellEditor implements TableCellEditor {
        private JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        private JButton downloadBtn = new JButton("下载");
        private JButton deleteBtn = new JButton("删除");
        private int editingRow;
        public ButtonEditor(JCheckBox checkBox) {
            panel.add(downloadBtn);
            panel.add(deleteBtn);
            downloadBtn.addActionListener(e -> {
                FileRecord rec = fileTableModel.getFileRecord(editingRow);
                // 复用提醒时的下载逻辑
                String docPath = System.getProperty("user.home") + File.separator + "Documents" + File.separator + "ChatFiles" + File.separator + currentUser;
                File userDir = new File(docPath);
                if (!userDir.exists()) userDir.mkdirs();
                JFileChooser fileChooser = new JFileChooser(userDir);
                fileChooser.setSelectedFile(new File(userDir, rec.fileName));
                if (fileChooser.showSaveDialog(parentFrame) == JFileChooser.APPROVE_OPTION) {
                    String savePath = fileChooser.getSelectedFile().getPath();
                    FileTransferManager.downloadFile(client.getSocket(), rec.fileId, savePath,
                        new FileTransferListener() {
                            @Override
                            public void onProgress(int percentage) {}
                            @Override
                            public void onComplete(String filePath) {
                                JOptionPane.showMessageDialog(parentFrame, "文件下载完成!\n保存路径: " + filePath);
                            }
                            @Override
                            public void onError(String error) {
                                JOptionPane.showMessageDialog(parentFrame, "下载失败: " + error, "错误", JOptionPane.ERROR_MESSAGE);
                            }
                        }
                    );
                }
                fireEditingStopped();
            });
            deleteBtn.addActionListener(e -> {
                FileRecord rec = fileTableModel.getFileRecord(editingRow);
                int opt = JOptionPane.showConfirmDialog(parentFrame, "确定要删除该文件记录吗?", "确认", JOptionPane.YES_NO_OPTION);
                if (opt == JOptionPane.YES_OPTION) {
                    List<FileRecord> list = FileHistoryXmlManager.loadHistory(currentUser, isGroup, targetId);
                    list.remove(rec);
                    FileHistoryXmlManager.saveHistory(currentUser, isGroup, targetId, list);
                    refreshFileTable();
                }
                fireEditingStopped();
            });
        }
        @Override
        public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
            editingRow = row;
            return panel;
        }
        @Override
        public Object getCellEditorValue() { return null; }
    }
}
