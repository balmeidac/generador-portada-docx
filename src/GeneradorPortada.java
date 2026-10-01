import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.prefs.Preferences;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Generador de portada académica UTEQ (.docx, A4) sin librerías externas.
 * Los logos (logo_universidad.png y logo_facultad.png) son opcionales: si existen junto a esta clase
 * (en la raíz de "src" en IntelliJ) o en la carpeta desde donde se ejecuta el programa, se insertan.
 *
 * @author Byron Omar Almeida Coello
 * @version 1.0
 * @license MIT
 */
public class GeneradorPortada {

    // ===== Datos fijos de la portada =====
    static final String UNIVERSIDAD = "UNIVERSIDAD TÉCNICA ESTATAL DE QUEVEDO";
    static final String FACULTAD_1 = "FACULTAD DE CIENCIAS DE LA COMPUTACIÓN";
    static final String FACULTAD_2 = "Y DISEÑO DIGITAL";
    static final String CARRERA = "CARRERA DE SISTEMAS DE INFORMACIÓN";
    static final String PARALELO = "SISINF – PARALELO A";
    static final String CODIGO = "SISINF-A";
    static final String LUGAR = "QUEVEDO – ECUADOR";
    static final String LOGO_UNIVERSIDAD = "logo_universidad.png";
    static final String LOGO_FACULTAD = "logo_facultad.png";

    static final String NS =
            "xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\" "
                    + "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\" "
                    + "xmlns:wp=\"http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing\" "
                    + "xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" "
                    + "xmlns:pic=\"http://schemas.openxmlformats.org/drawingml/2006/picture\"";

    static int idImagen = 1;

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }
        SwingUtilities.invokeLater(GeneradorPortada::mostrarVentana);
    }

    // ===================== VENTANA =====================
    static void mostrarVentana() {
        JFrame f = new JFrame("Generador de portada - UTEQ");
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        JTextField txtMateria = new JTextField(32);
        JTextField txtTema = new JTextField(32);
        JComboBox<String> cmbTipo = new JComboBox<>(new String[]{"Autor", "Autores", "Grupo"});
        JTextField txtAutor = new JTextField(32);
        JTextField txtDocente = new JTextField(32);
        Preferences prefs = Preferences.userNodeForPackage(GeneradorPortada.class);
        JTextField txtPropiedades = new JTextField(prefs.get("autorPropiedades", ""), 32);
        txtPropiedades.setToolTipText("Nombre que aparece en Detalles > Autores del archivo. Si lo dejas vacío se usa el autor o grupo.");
        JSpinner spnHojas = new JSpinner(new SpinnerNumberModel(3, 0, 50, 1));

        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(5, 5, 5, 5);
        c.anchor = GridBagConstraints.WEST;

        agregarFila(p, c, 0, new JLabel("Materia:"), txtMateria);
        agregarFila(p, c, 1, new JLabel("Tema:"), txtTema);
        agregarFila(p, c, 2, cmbTipo, txtAutor);
        agregarFila(p, c, 3, new JLabel("Profesor:"), txtDocente);
        agregarFila(p, c, 4, new JLabel("Autor en propiedades:"), txtPropiedades);
        agregarFila(p, c, 5, new JLabel("Hojas de figuras:"), spnHojas);

        JButton btnGenerar = new JButton("Generar documento");
        JButton btnSalir = new JButton("Salir");
        JPanel botones = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        botones.add(btnSalir);
        botones.add(btnGenerar);
        c.gridx = 0;
        c.gridy = 6;
        c.gridwidth = 2;
        c.fill = GridBagConstraints.HORIZONTAL;
        p.add(botones, c);

        btnSalir.addActionListener(e -> System.exit(0));
        btnGenerar.addActionListener(e -> {
            String materia = txtMateria.getText().trim();
            String tema = txtTema.getText().trim();
            String autor = txtAutor.getText().trim();
            String docente = txtDocente.getText().trim();
            if (materia.isEmpty() || tema.isEmpty() || autor.isEmpty() || docente.isEmpty()) {
                JOptionPane.showMessageDialog(f, "Completa todos los campos.", "Faltan datos",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }
            JFileChooser fc = new JFileChooser();
            fc.setDialogTitle("Guardar documento");
            fc.setFileFilter(new FileNameExtensionFilter("Documento de Word (*.docx)", "docx"));
            fc.setSelectedFile(new File(tema.replaceAll("[\\\\/:*?\"<>|]", "") + ".docx"));
            if (fc.showSaveDialog(f) != JFileChooser.APPROVE_OPTION) return;
            File destino = fc.getSelectedFile();
            if (!destino.getName().toLowerCase().endsWith(".docx")) {
                destino = new File(destino.getParentFile(), destino.getName() + ".docx");
            }
            if (destino.exists() && JOptionPane.showConfirmDialog(f,
                    "El archivo ya existe. ¿Deseas reemplazarlo?", "Confirmar",
                    JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
            try {
                String propiedades = txtPropiedades.getText().trim().isEmpty() ? autor : txtPropiedades.getText().trim();
                prefs.put("autorPropiedades", txtPropiedades.getText().trim());
                generar(materia, tema, (String) cmbTipo.getSelectedItem(), autor, docente,
                        (Integer) spnHojas.getValue(), destino, propiedades);
                if (leerLogo(LOGO_UNIVERSIDAD) == null || leerLogo(LOGO_FACULTAD) == null) {
                    JOptionPane.showMessageDialog(f, "No se encontraron los logos (" + LOGO_UNIVERSIDAD + " y "
                            + LOGO_FACULTAD + ").\nEl documento se generó sin ellos. Colócalos junto al programa para incluirlos.",
                            "Aviso", JOptionPane.INFORMATION_MESSAGE);
                }
                if (JOptionPane.showConfirmDialog(f, "Documento generado.\n¿Deseas abrirlo ahora?",
                        "Listo", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                    try {
                        Desktop.getDesktop().open(destino);
                    } catch (Exception ex) {
                        JOptionPane.showMessageDialog(f, "No se pudo abrir el archivo automáticamente.");
                    }
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(f, "Error al generar el documento:\n" + ex.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        f.setContentPane(p);
        f.pack();
        f.setResizable(false);
        f.setLocationRelativeTo(null);
        f.setVisible(true);
    }

    static void agregarFila(JPanel p, GridBagConstraints c, int fila, Component etiqueta, Component campo) {
        c.gridy = fila;
        c.gridwidth = 1;
        c.fill = GridBagConstraints.NONE;
        c.gridx = 0;
        p.add(etiqueta, c);
        c.gridx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        p.add(campo, c);
    }

    // ===================== GENERACIÓN DEL DOCX =====================
    static void generar(String materia, String tema, String tipo, String autor, String docente,
                        int hojas, File destino, String autorPropiedades) throws IOException {
        Locale es = Locale.forLanguageTag("es-EC");
        materia = materia.trim().toUpperCase(es);
        tema = tema.trim().toUpperCase(es);
        autor = autor.trim().toUpperCase(es);
        docente = docente.trim().toUpperCase(es);
        autorPropiedades = autorPropiedades.trim().toUpperCase(es);
        String etiquetaAutor = tipo.toUpperCase(es) + ":";
        String anio = String.valueOf(LocalDate.now().getYear());
        idImagen = 1;

        byte[] logoUni = leerLogo(LOGO_UNIVERSIDAD);
        byte[] logoFac = leerLogo(LOGO_FACULTAD);
        long anchoLogo = cm(3.7);
        StringBuilder logos = new StringBuilder();
        if (logoUni != null) {
            logos.append(imagen("rId4", "Logo universidad", anchoLogo, anchoLogo * alto(logoUni) / ancho(logoUni)));
        }
        if (logoUni != null && logoFac != null) logos.append(run("   ", 28, false, false));
        if (logoFac != null) {
            logos.append(imagen("rId5", "Logo facultad", anchoLogo, anchoLogo * alto(logoFac) / ancho(logoFac)));
        }

        // ---- Portada ----
        StringBuilder body = new StringBuilder();
        if (logos.length() > 0) {
            body.append(par("<w:spacing w:before=\"0\" w:after=\"240\" w:line=\"240\" w:lineRule=\"auto\"/><w:jc w:val=\"center\"/>",
                    logos.toString()));
        }
        body.append(centro(UNIVERSIDAD, 36, 0, 160));
        body.append(centro(FACULTAD_1, 32, 0, 120));
        body.append(centro(FACULTAD_2, 32, 0, 360));
        body.append(centro(CARRERA, 28, 0, 480));
        body.append(centro(materia, 26, 0, 360));
        body.append(centro(PARALELO, 26, 0, 480));
        body.append(centro("TEMA", 28, 0, 120));
        body.append(centro(tema, 24, 0, 480));
        body.append(centro(etiquetaAutor, 28, 0, 120));
        body.append(centro(autor, 24, 0, 480));
        body.append(centro("DOCENTE:", 28, 0, 120));
        body.append(centro(docente, 24, 0, 480));
        body.append(centro(LUGAR, 28, 0, 120));
        body.append(centro(anio, 24, 0, 0));

        // ---- Hojas de figuras con placeholder ----
        Map<String, byte[]> partes = new LinkedHashMap<>();
        StringBuilder relsFiguras = new StringBuilder();
        long anchoFig = cm(15.9), altoFig = cm(8.9);
        for (int i = 1; i <= hojas; i++) {
            String rid = "rId" + (10 + i);
            partes.put("word/media/placeholder" + i + ".png", crearPlaceholder());
            relsFiguras.append("<Relationship Id=\"").append(rid)
                    .append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/image\" Target=\"media/placeholder")
                    .append(i).append(".png\"/>");

            body.append(par("<w:keepNext/><w:pageBreakBefore/><w:spacing w:before=\"0\" w:after=\"0\" w:line=\"360\" w:lineRule=\"auto\"/>",
                    run("Figura " + i, 22, true, false)));
            body.append(par("<w:keepNext/><w:spacing w:before=\"0\" w:after=\"120\" w:line=\"360\" w:lineRule=\"auto\"/>",
                    run("[Título de la figura]", 22, false, true)));
            body.append(par("<w:keepNext/><w:spacing w:before=\"0\" w:after=\"120\" w:line=\"240\" w:lineRule=\"auto\"/><w:jc w:val=\"center\"/>",
                    imagen(rid, "Espacio para la figura " + i, anchoFig, altoFig)));
            body.append(par("<w:spacing w:before=\"0\" w:after=\"120\" w:line=\"360\" w:lineRule=\"auto\"/>",
                    run("Pie de figura. ", 22, false, true)
                            + run("[Fuente o aclaración breve de la figura.]", 22, false, false)));
            body.append(par("<w:spacing w:before=\"0\" w:after=\"0\" w:line=\"360\" w:lineRule=\"auto\"/><w:ind w:firstLine=\"720\"/>",
                    run("[Escribe aquí la descripción de la figura.]", 22, false, false)));
        }

        String sectPr = "<w:sectPr><w:headerReference w:type=\"default\" r:id=\"rId2\"/>"
                + "<w:footerReference w:type=\"default\" r:id=\"rId3\"/>"
                + "<w:pgSz w:w=\"11906\" w:h=\"16838\"/>"
                + "<w:pgMar w:top=\"1440\" w:right=\"1440\" w:bottom=\"1440\" w:left=\"1440\" w:header=\"708\" w:footer=\"708\" w:gutter=\"0\"/>"
                + "<w:titlePg/></w:sectPr>";

        String documento = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<w:document " + NS + "><w:body>" + body + sectPr + "</w:body></w:document>";

        String encabezado = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<w:hdr " + NS + ">"
                + par("<w:spacing w:before=\"0\" w:after=\"0\" w:line=\"240\" w:lineRule=\"auto\"/><w:jc w:val=\"center\"/>",
                run("| " + materia + " | " + CODIGO + " | " + autor + " |", 18, false, false))
                + "</w:hdr>";

        String pie = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<w:ftr " + NS + ">"
                + par("<w:spacing w:before=\"0\" w:after=\"0\" w:line=\"240\" w:lineRule=\"auto\"/><w:jc w:val=\"right\"/>",
                "<w:fldSimple w:instr=\" PAGE \"><w:r><w:rPr><w:sz w:val=\"22\"/></w:rPr><w:t>2</w:t></w:r></w:fldSimple>")
                + "</w:ftr>";

        String estilos = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<w:styles xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">"
                + "<w:docDefaults><w:rPrDefault><w:rPr>"
                + "<w:rFonts w:ascii=\"Arial\" w:hAnsi=\"Arial\" w:eastAsia=\"Arial\" w:cs=\"Arial\"/>"
                + "<w:sz w:val=\"22\"/><w:szCs w:val=\"22\"/><w:lang w:val=\"es-EC\"/></w:rPr></w:rPrDefault>"
                + "<w:pPrDefault><w:pPr><w:spacing w:after=\"0\" w:line=\"360\" w:lineRule=\"auto\"/></w:pPr></w:pPrDefault>"
                + "</w:docDefaults>"
                + "<w:style w:type=\"paragraph\" w:default=\"1\" w:styleId=\"Normal\"><w:name w:val=\"Normal\"/><w:qFormat/></w:style>"
                + "</w:styles>";

        String contentTypes = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                + "<Default Extension=\"png\" ContentType=\"image/png\"/>"
                + "<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>"
                + "<Override PartName=\"/word/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml\"/>"
                + "<Override PartName=\"/word/header1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.header+xml\"/>"
                + "<Override PartName=\"/word/footer1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.footer+xml\"/>"
                + "<Override PartName=\"/docProps/core.xml\" ContentType=\"application/vnd.openxmlformats-package.core-properties+xml\"/>"
                + "<Override PartName=\"/docProps/app.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.extended-properties+xml\"/>"
                + "</Types>";

        String relsRaiz = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/>"
                + "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties\" Target=\"docProps/core.xml\"/>"
                + "<Relationship Id=\"rId3\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/extended-properties\" Target=\"docProps/app.xml\"/>"
                + "</Relationships>";

        String ahora = java.time.Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS).toString();
        String core = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<cp:coreProperties xmlns:cp=\"http://schemas.openxmlformats.org/package/2006/metadata/core-properties\" "
                + "xmlns:dc=\"http://purl.org/dc/elements/1.1/\" xmlns:dcterms=\"http://purl.org/dc/terms/\" "
                + "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">"
                + "<dc:title>" + esc(tema) + "</dc:title>"
                + "<dc:creator>" + esc(autorPropiedades) + "</dc:creator>"
                + "<cp:lastModifiedBy>" + esc(autorPropiedades) + "</cp:lastModifiedBy>"
                + "<dcterms:created xsi:type=\"dcterms:W3CDTF\">" + ahora + "</dcterms:created>"
                + "<dcterms:modified xsi:type=\"dcterms:W3CDTF\">" + ahora + "</dcterms:modified>"
                + "</cp:coreProperties>";
        String app = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Properties xmlns=\"http://schemas.openxmlformats.org/officeDocument/2006/extended-properties\">"
                + "<Application>Microsoft Office Word</Application></Properties>";

        String relsDoc = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>"
                + "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/header\" Target=\"header1.xml\"/>"
                + "<Relationship Id=\"rId3\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/footer\" Target=\"footer1.xml\"/>"
                + (logoUni == null ? "" : "<Relationship Id=\"rId4\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/image\" Target=\"media/logo_universidad.png\"/>")
                + (logoFac == null ? "" : "<Relationship Id=\"rId5\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/image\" Target=\"media/logo_facultad.png\"/>")
                + relsFiguras
                + "</Relationships>";

        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(destino))) {
            escribir(zip, "[Content_Types].xml", contentTypes.getBytes(StandardCharsets.UTF_8));
            escribir(zip, "_rels/.rels", relsRaiz.getBytes(StandardCharsets.UTF_8));
            escribir(zip, "docProps/core.xml", core.getBytes(StandardCharsets.UTF_8));
            escribir(zip, "docProps/app.xml", app.getBytes(StandardCharsets.UTF_8));
            escribir(zip, "word/document.xml", documento.getBytes(StandardCharsets.UTF_8));
            escribir(zip, "word/_rels/document.xml.rels", relsDoc.getBytes(StandardCharsets.UTF_8));
            escribir(zip, "word/styles.xml", estilos.getBytes(StandardCharsets.UTF_8));
            escribir(zip, "word/header1.xml", encabezado.getBytes(StandardCharsets.UTF_8));
            escribir(zip, "word/footer1.xml", pie.getBytes(StandardCharsets.UTF_8));
            if (logoUni != null) escribir(zip, "word/media/logo_universidad.png", logoUni);
            if (logoFac != null) escribir(zip, "word/media/logo_facultad.png", logoFac);
            for (Map.Entry<String, byte[]> e : partes.entrySet()) escribir(zip, e.getKey(), e.getValue());
        }
    }

    // ===================== UTILIDADES =====================
    static void escribir(ZipOutputStream zip, String nombre, byte[] datos) throws IOException {
        zip.putNextEntry(new ZipEntry(nombre));
        zip.write(datos);
        zip.closeEntry();
    }

    static byte[] leerLogo(String nombre) throws IOException {
        try (InputStream in = GeneradorPortada.class.getResourceAsStream("/" + nombre)) {
            if (in != null) {
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) != -1) bo.write(buf, 0, n);
                return bo.toByteArray();
            }
        }
        File f = new File(nombre);
        if (f.exists()) return java.nio.file.Files.readAllBytes(f.toPath());
        return null;
    }

    static int ancho(byte[] png) throws IOException {
        return ImageIO.read(new ByteArrayInputStream(png)).getWidth();
    }

    static int alto(byte[] png) throws IOException {
        return ImageIO.read(new ByteArrayInputStream(png)).getHeight();
    }

    static long cm(double v) {
        return Math.round(v * 360000);
    }

    static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    static String run(String texto, int sz, boolean negrita, boolean cursiva) {
        return "<w:r><w:rPr>" + (negrita ? "<w:b/>" : "") + (cursiva ? "<w:i/>" : "")
                + "<w:sz w:val=\"" + sz + "\"/><w:szCs w:val=\"" + sz + "\"/></w:rPr>"
                + "<w:t xml:space=\"preserve\">" + esc(texto) + "</w:t></w:r>";
    }

    static String par(String propiedades, String contenido) {
        return "<w:p>" + (propiedades.isEmpty() ? "" : "<w:pPr>" + propiedades + "</w:pPr>") + contenido + "</w:p>";
    }

    static String centro(String texto, int sz, int antes, int despues) {
        return par("<w:spacing w:before=\"" + antes + "\" w:after=\"" + despues
                + "\" w:line=\"240\" w:lineRule=\"auto\"/><w:jc w:val=\"center\"/>", run(texto, sz, true, false));
    }

    static String imagen(String rid, String descripcion, long cx, long cy) {
        int id = idImagen++;
        return "<w:r><w:drawing><wp:inline distT=\"0\" distB=\"0\" distL=\"0\" distR=\"0\">"
                + "<wp:extent cx=\"" + cx + "\" cy=\"" + cy + "\"/>"
                + "<wp:docPr id=\"" + id + "\" name=\"Imagen " + id + "\" descr=\"" + esc(descripcion) + "\"/>"
                + "<wp:cNvGraphicFramePr><a:graphicFrameLocks noChangeAspect=\"1\"/></wp:cNvGraphicFramePr>"
                + "<a:graphic><a:graphicData uri=\"http://schemas.openxmlformats.org/drawingml/2006/picture\">"
                + "<pic:pic><pic:nvPicPr><pic:cNvPr id=\"" + id + "\" name=\"Imagen " + id + "\"/><pic:cNvPicPr/></pic:nvPicPr>"
                + "<pic:blipFill><a:blip r:embed=\"" + rid + "\"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill>"
                + "<pic:spPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"" + cx + "\" cy=\"" + cy + "\"/></a:xfrm>"
                + "<a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom></pic:spPr></pic:pic>"
                + "</a:graphicData></a:graphic></wp:inline></w:drawing></w:r>";
    }

    /** Imagen gris de relleno; en Word: clic derecho > Cambiar imagen conserva el tamaño. */
    static byte[] crearPlaceholder() throws IOException {
        int w = 1600, h = 900;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(0xF2F2F2));
        g.fillRect(0, 0, w, h);
        g.setColor(new Color(0xA6A6A6));
        g.setStroke(new BasicStroke(6));
        g.drawRect(3, 3, w - 7, h - 7);
        int cx = w / 2, cy = h / 2 - 70;
        g.setStroke(new BasicStroke(8));
        g.drawRoundRect(cx - 130, cy - 95, 260, 190, 26, 26);
        g.fillOval(cx + 45, cy - 65, 38, 38);
        g.fillPolygon(new int[]{cx - 105, cx - 35, cx + 5, cx + 50, cx + 105},
                new int[]{cy + 70, cy - 10, cy + 40, cy + 5, cy + 70}, 5);
        g.setFont(new Font("SansSerif", Font.BOLD, 52));
        String t1 = "ESPACIO PARA IMAGEN";
        g.drawString(t1, cx - g.getFontMetrics().stringWidth(t1) / 2, cy + 190);
        g.setFont(new Font("SansSerif", Font.PLAIN, 34));
        String t2 = "Clic derecho > Cambiar imagen";
        g.drawString(t2, cx - g.getFontMetrics().stringWidth(t2) / 2, cy + 250);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }
}
