package package1;
import java.applet.Applet;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

public class MathVisualizer extends Applet
        implements ActionListener, MouseListener, MouseMotionListener {

    private TextField input;
    private Button plotBtn, clearBtn, zoomInBtn, zoomOutBtn, resetBtn;
    private Checkbox showTangent;

    private final List<Func> funcs = new ArrayList<>();
    private final Color[] palette = new Color[]{
            new Color(0,70,200), new Color(200,30,120),
            new Color(0,140,80), new Color(230,120,20)
    };

    private double scale = 60.0;   // pixels per unit
    private int originX = -1, originY = -1;
    private boolean panning = false;
    private int lastX, lastY;
    private int mouseX = 0, mouseY = 0;

    public void init() {
        setBackground(Color.white);
        setLayout(new BorderLayout());

        Panel top = new Panel(new FlowLayout(FlowLayout.LEFT));
        top.add(new Label("y ="));
        input = new TextField("sin(x)", 24);
        plotBtn = new Button("Plot");
        clearBtn = new Button("Clear");
        zoomInBtn = new Button("Zoom In");
        zoomOutBtn = new Button("Zoom Out");
        resetBtn = new Button("Reset View");
        showTangent = new Checkbox("Tangent @ cursor", true);

        top.add(input);
        top.add(plotBtn);
        top.add(clearBtn);
        top.add(zoomInBtn);
        top.add(zoomOutBtn);
        top.add(resetBtn);
        top.add(showTangent);
        add(top, BorderLayout.NORTH);

        plotBtn.addActionListener(this);
        clearBtn.addActionListener(this);
        zoomInBtn.addActionListener(this);
        zoomOutBtn.addActionListener(this);
        resetBtn.addActionListener(this);

        addMouseListener(this);
        addMouseMotionListener(this);
    }

    public void actionPerformed(ActionEvent e) {
        Object src = e.getSource();
        if (src == plotBtn) {
            String s = input.getText().trim();
            if (s.startsWith("y=") || s.startsWith("Y=")) s = s.substring(2).trim();
            if (s.length() == 0) { showStatus("Enter an expression."); return; }
            try {
                Func f = new Func(s, palette[funcs.size() % palette.length]);
                // quick sanity check
                f.eval(0.0);
                funcs.add(f);
                showStatus("Plotted: y = " + s);
            } catch (RuntimeException ex) {
                showStatus("Parse error: " + ex.getMessage());
            }
        } else if (src == clearBtn) {
            funcs.clear();
            showStatus("Cleared.");
        } else if (src == zoomInBtn) {
            scale *= 1.2;
        } else if (src == zoomOutBtn) {
            scale /= 1.2;
            if (scale < 6) scale = 6;
        } else if (src == resetBtn) {
            scale = 60.0;
            originX = -1; originY = -1; // will recenter in paint
        }
        repaint();
    }

    public void update(Graphics g) { paint(g); } // reduce flicker

    public void paint(Graphics g) {
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;

        if (originX < 0 || originY < 0) { originX = w / 2; originY = h / 2; }

        // double buffer
        BufferedImage buf = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = buf.createGraphics();
        g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                java.awt.RenderingHints.VALUE_ANTIALIAS_ON);

        // background
        g2.setColor(Color.white);
        g2.fillRect(0, 0, w, h);

        // grid + axes
        drawGrid(g2, w, h);
        drawAxes(g2, w, h);

        // functions (if any)
        int totalSegments = drawFunctions(g2, w, h);

        // cursor info
        double wx = screenToWorldX(mouseX);
        double wy = screenToWorldY(mouseY);
        g2.setColor(new Color(0,0,0,160));
        g2.drawString(String.format("x=%.4f  y=%.4f    funcs=%d  segs=%d",
                wx, wy, funcs.size(), totalSegments), 10, h - 10);

        
        g2.setColor(Color.red);
        g2.fillOval(mouseX - 3, mouseY - 3, 6, 6); // small red dot
        g2.setColor(Color.blue);
        g2.drawString(String.format("(%.2f, %.2f)", wx, wy), mouseX + 10, mouseY - 10);

        g.drawImage(buf, 0, 0, null);
        g2.dispose();
    }



    private void drawGrid(Graphics2D g2, int w, int h) {
        int step = Math.max(20, (int)Math.round(scale)); // pixels per unit grid
        int startX = ((originX % step) + step) % step;
        int startY = ((originY % step) + step) % step;

        g2.setColor(new Color(245,245,245));
        for (int x = startX; x < w; x += step) g2.drawLine(x, 0, x, h);
        for (int y = startY; y < h; y += step) g2.drawLine(0, y, w, y);

        // ticks
        g2.setColor(new Color(200,200,200));
        for (int x = startX; x < w; x += step) g2.drawLine(x, originY - 3, x, originY + 3);
        for (int y = startY; y < h; y += step) g2.drawLine(originX - 3, y, originX + 3, y);
    }

    private void drawAxes(Graphics2D g2, int w, int h) {
        g2.setColor(new Color(120,120,120));
        g2.setStroke(new BasicStroke(1.2f));
        g2.drawLine(0, originY, w, originY);
        g2.drawLine(originX, 0, originX, h);
    }

    
    private int drawFunctions(Graphics2D g2, int w, int h) {
        int segments = 0;
        for (int i = 0; i < funcs.size(); i++) {
            Func f = funcs.get(i);
            g2.setColor(f.color);
            g2.setStroke(new BasicStroke(1.4f));
            int prevX = -1, prevY = -1;
            boolean prevOk = false;

            for (int sx = 0; sx < w; sx++) {
                double x = screenToWorldX(sx);
                double y;
                try {
                    y = f.eval(x);
                } catch (RuntimeException ex) {
                    prevOk = false;
                    continue;
                }
                if (Double.isNaN(y) || Double.isInfinite(y)) { prevOk = false; continue; }
                int sy = worldToScreenY(y);

                // avoid connecting lines across huge jumps
                if (prevOk && Math.abs(sy - prevY) < h) {
                    g2.drawLine(prevX, prevY, sx, sy);
                    segments++;
                }
                prevX = sx; prevY = sy; prevOk = true;
            }
        }

        
        if (showTangent.getState() && !funcs.isEmpty()) {
            Func f = funcs.get(funcs.size() - 1);
            double x0 = screenToWorldX(mouseX);
            try {
                double y0 = f.eval(x0);
                if (!Double.isNaN(y0) && !Double.isInfinite(y0)) {
                    double hstep = 1e-4;
                    double slope = (f.eval(x0 + hstep) - f.eval(x0 - hstep)) / (2 * hstep);
                    int px = 120;
                    double dxWorld = px / scale;
                    int xA = worldToScreenX(x0 - dxWorld), yA = worldToScreenY(y0 - slope * dxWorld);
                    int xB = worldToScreenX(x0 + dxWorld), yB = worldToScreenY(y0 + slope * dxWorld);
                    g2.setColor(new Color(0,0,0,160));
                    Stroke old = g2.getStroke();
                    g2.setStroke(new BasicStroke(1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 1f, new float[]{6f,6f}, 0f));
                    g2.drawLine(xA, yA, xB, yB);
                    g2.setStroke(old);
                    g2.setColor(new Color(0,0,0,180));
                    g2.drawString(String.format("y' ≈ %.4f", slope), mouseX + 10, mouseY - 12);
                }
            } catch (RuntimeException ignored) {}
        }
        return segments;
    }

    
    private double screenToWorldX(int sx) { return (sx - originX) / scale; }
    private double screenToWorldY(int sy) { return (originY - sy) / scale; }
    private int worldToScreenX(double x)   { return (int)Math.round(originX + x * scale); }
    private int worldToScreenY(double y)   { return (int)Math.round(originY - y * scale); }

    
    public void mousePressed(MouseEvent e) { panning = true; lastX = e.getX(); lastY = e.getY(); }
    public void mouseReleased(MouseEvent e) { panning = false; }
    public void mouseDragged(MouseEvent e) {
        if (panning) {
            int dx = e.getX() - lastX, dy = e.getY() - lastY;
            originX += dx; originY += dy;
            lastX = e.getX(); lastY = e.getY();
            repaint();
        }
    }
    public void mouseMoved(MouseEvent e) { mouseX = e.getX(); mouseY = e.getY(); repaint(); }
    public void mouseClicked(MouseEvent e) {}
    public void mouseEntered(MouseEvent e) {}
    public void mouseExited(MouseEvent e) {}

    // wrapper for function expression
    private static class Func {
        final String expr;
        final Color color;
        final ExprParser parser;
        Func(String expr, Color color) {
            this.expr = expr.replaceAll("\\s+", "");
            this.color = color;
            this.parser = new ExprParser(this.expr);
        }
        double eval(double x) { return parser.eval(x); }
    }

    // Recursive-descent parser
    private static class ExprParser {
        private final String s;
        private int pos = -1;
        private int ch;
        private double xVar;

        ExprParser(String s) { this.s = s; }

        double eval(double x) {
            this.xVar = x;
            pos = -1;
            nextChar();
            double v = parseExpression();
            if (ch != -1) throw new RuntimeException("Unexpected: '" + (char)ch + "'");
            return v;
        }

        private void nextChar() { ch = (++pos < s.length()) ? s.charAt(pos) : -1; }
        private boolean eat(int c) {
            while (ch == ' ') nextChar();
            if (ch == c) { nextChar(); return true; }
            return false;
        }

        private double parseExpression() {
            double v = parseTerm();
            for (;;) {
                if (eat('+')) v += parseTerm();
                else if (eat('-')) v -= parseTerm();
                else return v;
            }
        }

        private double parseTerm() {
            double v = parsePower();
            for (;;) {
                if (eat('*')) v *= parsePower();
                else if (eat('/')) v /= parsePower();
                else return v;
            }
        }

        private double parsePower() {
            double v = parseFactor();
            if (eat('^')) v = Math.pow(v, parsePower()); // right-associative
            return v;
        }

        private double parseFactor() {
            if (eat('+')) return parseFactor();
            if (eat('-')) return -parseFactor();

            double v;
            int start = this.pos;

            if (eat('(')) {
                v = parseExpression();
                if (!eat(')')) throw new RuntimeException("Missing ')'");
            } else if ((ch >= '0' && ch <= '9') || ch == '.') {
                while ((ch >= '0' && ch <= '9') || ch == '.') nextChar();
                v = Double.parseDouble(s.substring(start, this.pos));
            } else if (ch >= 'a' && ch <= 'z') {
                while (ch >= 'a' && ch <= 'z') nextChar();
                String name = s.substring(start, this.pos).toLowerCase();

                if (name.equals("x")) return xVar;
                if (name.equals("pi")) return Math.PI;
                if (name.equals("e")) return Math.E;

                double arg = parseFactor();
                switch (name) {
                    case "sin": return Math.sin(arg);
                    case "cos": return Math.cos(arg);
                    case "tan": return Math.tan(arg);
                    case "sqrt": return Math.sqrt(arg);
                    case "abs": return Math.abs(arg);
                    case "log": return Math.log10(arg);
                    case "ln": return Math.log(arg);
                    case "exp": return Math.exp(arg);
                    default: throw new RuntimeException("Unknown function: " + name);
                }
            } else {
                throw new RuntimeException("Unexpected: '" + (char)ch + "'");
            }
            return v;
        }
    }
}