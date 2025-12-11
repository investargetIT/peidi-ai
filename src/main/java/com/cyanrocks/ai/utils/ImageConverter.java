package com.cyanrocks.ai.utils;

import fr.opensagres.poi.xwpf.converter.pdf.PdfConverter;
import fr.opensagres.poi.xwpf.converter.pdf.PdfOptions;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @Author wjq
 * @Date 2025/11/3 17:21
 */
@Service
public class ImageConverter {

    private static final Logger logger = Logger.getLogger(FileToMarkdownConverter.class.getName());

    @Autowired
    private OssUtils ossUtils;

    public List<BufferedImage> docxToImages(Path docxPath) {
        List<BufferedImage> images = new ArrayList<>();

        try (XWPFDocument document = new XWPFDocument(Files.newInputStream(docxPath))) {
            Path tempPdf = Files.createTempFile("docx_convert", ".pdf");

            PdfOptions options = PdfOptions.create();

            try (FileOutputStream out = new FileOutputStream(tempPdf.toFile())) {
                PdfConverter.getInstance().convert(document, out, options);
            }
            ossUtils.uploadToOss("ai/"+tempPdf.getFileName(), Files.readAllBytes(tempPdf));
            images = pdfToImages(tempPdf);
            Files.deleteIfExists(tempPdf);

            logger.info("成功将 DOCX 文件转换为 " + images.size() + " 张图像");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "转换 DOCX 文件为图像时出错: " + docxPath, e);
        }
        return images;
    }

    public List<BufferedImage> pdfToImages(Path pdfPath) {
        List<BufferedImage> images = new ArrayList<>();
        final int DPI = 200;

        try (PDDocument document = PDDocument.load(pdfPath.toFile())) {
            PDFRenderer renderer = new PDFRenderer(document);

            for (int page = 0; page < document.getNumberOfPages(); page++) {
                BufferedImage image = renderer.renderImageWithDPI(page, DPI);
                images.add(image);
            }
            logger.info("成功将文件转换为 " + images.size() + " 张图像");
        } catch (IOException e) {
            logger.log(Level.SEVERE, "转换文件为图像时出错: " + pdfPath, e);
        }
        return images;
    }

    public BufferedImage toBufferedImage(MultipartFile file) {

        try (InputStream inputStream = file.getInputStream()) {
            BufferedImage image = ImageIO.read(inputStream);

            if (image == null) {
                throw new IllegalArgumentException("不支持的图片格式或文件已损坏");
            }

            return image;
        } catch (IOException e) {
            throw new RuntimeException("图片转换失败: " + e.getMessage(), e);
        }
    }
}
