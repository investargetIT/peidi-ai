package com.cyanrocks.ai.utils;

import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * @Author wjq
 * @Date 2024/11/7 17:14
 */
@Component
public class ExcelUtils {

    public byte[] buildExcel(List<String> headers, List<Map<String,Object>> records){
        try {
            Workbook workbook = new XSSFWorkbook();
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            Sheet sheet = workbook.createSheet("Sheet");

            // 创建表头
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.size(); i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers.get(i));
            }

            // 添加数据
            for (int i = 0; i < records.size(); i++) {
                Row row = sheet.createRow(i + 1); // 从第二行开始添加数据
                Map<String, Object> record = records.get(i);
                for (int j = 0; j < headers.size(); j++) {
                    String header = headers.get(j);
                    Object value = record.get(header);
                    row.createCell(j).setCellValue(value != null ? value.toString() : ""); // 处理 null 值
                }
            }

            // 将 Excel 写入输出流
            workbook.write(outputStream);
            return outputStream.toByteArray();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public String getCellValue(Cell cell) {
        if (cell == null) {
            return null;
        }
        switch (cell.getCellType()) {
            case STRING:
                if (StringUtils.isBlank(cell.getStringCellValue())){
                    return null;
                }
                return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    Date date = cell.getDateCellValue();
                    LocalDateTime localDateTime = LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
                    String timeStr = localDateTime.toString().replace("T", " ").trim();
                    if (timeStr.matches("\\d{4}-\\d{2}-\\d{2}")) {
                        // Format: yyyy-MM-dd
                        return timeStr + " 00:00:00";
                    } else if (timeStr.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}")) {
                        // Format: yyyy-MM-dd HH
                        return timeStr + ":00:00";
                    } else if (timeStr.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}")) {
                        // Format: yyyy-MM-dd HH:mm
                        return timeStr + ":00";
                    } else {
                        return timeStr;
                    }
                } else {
                    return String.valueOf(new DecimalFormat("#.####").format(cell.getNumericCellValue()));
                }
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                String formula = cell.getCellFormula();
                if (formula.matches("[A-Z]+\\d+")) {
                    // 如果公式是单纯的单元格引用（如A1, B2等），直接返回引用的单元格值
                    CellReference cellReference = new CellReference(formula);
                    Cell referencedCell = cell.getSheet().getRow(cellReference.getRow()).getCell(cellReference.getCol());
                    return getCellValue(referencedCell);
                } else if (formula.startsWith("_xlfn.")) {
                    try {
                        return cell.getStringCellValue();
                    }catch (IllegalStateException e){
                        return null;
                    }
                } else {
                    // 如果是复杂公式，返回公式字符串
                    // 去掉首尾引号
                    if (formula.startsWith("\"") && formula.endsWith("\"")) {
                        formula = formula.substring(1, formula.length() - 1);
                    }
                    return formula;
                }
            default:
                return null;
        }
    }

    public LocalDateTime parseDate(String date, String pattern) {
        if (StringUtils.isBlank(date)) {
            return null;
        }
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
        return LocalDateTime.parse(date, formatter);
    }
}
