package com.docmind.service;

import com.docmind.common.BizException;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.text.TextContentRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 文档解析服务：把各种格式的文件统一提取成"纯文本"。
 *
 * 各格式的处理方式：
 *   md   —— commonmark 渲染成纯文本（去掉 #、*、[]() 等标记符号，检索时更干净）
 *   txt  —— 直接读 UTF-8
 *   pdf  —— 文字版 PDF 用 PDFBox 提取文字层；提取不到文字（扫描版）则把每页渲染成图片再 OCR
 *   image—— tess4j (Tesseract) OCR 直接识别
 *
 * OCR 优雅降级：tessdata 语言包缺失时图片/扫描PDF不报错，
 * 而是提取不到文本（文档仍可预览、可按文件名搜索），日志里明确提示原因。
 */
@Service
public class TextExtractService {

    private static final Logger log = LoggerFactory.getLogger(TextExtractService.class);

    /** PDF 文字层最多提取前 100 页（个人文档场景够用，防止超大 PDF 卡死） */
    private static final int MAX_PDF_TEXT_PAGES = 100;

    /** 文字层提取结果少于这个字符数时，认为它是"扫描版 PDF"，转 OCR */
    private static final int SCANNED_PDF_THRESHOLD = 50;

    @Value("${docmind.ocr.tessdata}")
    private String tessdataPath;

    @Value("${docmind.ocr.max-ocr-pages}")
    private int maxOcrPages;

    /** OCR 环境缺失时只警告一次，避免每个文件都刷日志 */
    private volatile boolean ocrWarned = false;

    /** 按文件类型分发到对应解析方法，统一返回纯文本 */
    public String extract(Path file, String fileType) {
        return switch (fileType) {
            case "md" -> extractMd(file);
            case "txt" -> extractTxt(file);
            case "pdf" -> extractPdf(file);
            case "image" -> ocrImage(file.toFile());
            default -> "";
        };
    }

    /** Markdown：用 commonmark 解析成纯文本（预览页仍展示原始 md 文件，不受影响） */
    private String extractMd(Path file) {
        try {
            Parser parser = Parser.builder().build();
            Node node = parser.parse(Files.readString(file, StandardCharsets.UTF_8));
            StringWriter writer = new StringWriter();
            TextContentRenderer.builder().build().render(node, writer);
            return writer.toString();
        } catch (Exception e) {
            throw new BizException("Markdown 解析失败: " + e.getMessage());
        }
    }

    private String extractTxt(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new BizException("TXT 解析失败（请确认文件是 UTF-8 编码）: " + e.getMessage());
        }
    }

    /**
     * PDF 解析，分两种情况：
     *   ① 文字版：直接提取文字层，快且准；
     *   ② 扫描版：文字层几乎没有 → 逐页渲染成 150dpi 图片 → OCR。
     * OCR 页数受 docmind.ocr.max-ocr-pages 限制，避免大扫描件耗时过久。
     */
    private String extractPdf(Path file) {
        try (PDDocument doc = PDDocument.load(file.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setEndPage(Math.min(doc.getNumberOfPages(), MAX_PDF_TEXT_PAGES));
            String text = stripper.getText(doc);
            if (text != null && text.strip().length() >= SCANNED_PDF_THRESHOLD) {
                return text;
            }
            // 文字层太少：大概率是扫描版
            if (!isOcrAvailable()) {
                log.warn("PDF 未提取到文字层且 OCR 语言包未配置，仅保留少量文本: {}", file.getFileName());
                return text == null ? "" : text;
            }
            log.info("PDF 未提取到文字层，按扫描版 OCR 处理: {}", file.getFileName());
            PDFRenderer renderer = new PDFRenderer(doc);
            StringBuilder sb = new StringBuilder(text == null ? "" : text);
            int pages = Math.min(doc.getNumberOfPages(), maxOcrPages);
            // Tesseract 实例不是线程安全的，每次解析新建一个
            Tesseract tesseract = newTesseract();
            for (int i = 0; i < pages; i++) {
                BufferedImage image = renderer.renderImageWithDPI(i, 150);
                try {
                    sb.append('\n').append(tesseract.doOCR(image));
                } catch (TesseractException e) {
                    log.warn("第 {} 页 OCR 失败: {}", i + 1, e.getMessage());
                }
            }
            return sb.toString();
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("PDF 解析失败: " + e.getMessage());
        }
    }

    /** 图片 OCR */
    private String ocrImage(File imageFile) {
        if (!isOcrAvailable()) {
            return "";
        }
        try {
            return newTesseract().doOCR(imageFile);
        } catch (TesseractException e) {
            throw new BizException("图片 OCR 失败: " + e.getMessage());
        }
    }

    /**
     * 检测 OCR 环境：tessdata 目录下是否有中文语言包 chi_sim.traineddata。
     * 没有就返回 false，调用方跳过 OCR（优雅降级），只警告一次。
     */
    public boolean isOcrAvailable() {
        boolean ok = Files.exists(Path.of(tessdataPath, "chi_sim.traineddata"));
        if (!ok && !ocrWarned) {
            ocrWarned = true;
            log.warn("未检测到 OCR 语言包: {}\\chi_sim.traineddata，图片和扫描版 PDF 将跳过文字识别。"
                    + "请按部署文档下载 tessdata 放到该目录。", Path.of(tessdataPath).toAbsolutePath());
        }
        return ok;
    }

    private Tesseract newTesseract() {
        Tesseract tesseract = new Tesseract();
        tesseract.setDatapath(tessdataPath);
        tesseract.setLanguage("chi_sim+eng");   // 中文简体 + 英文一起识别
        return tesseract;
    }
}
