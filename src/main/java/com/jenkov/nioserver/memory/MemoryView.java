package com.jenkov.nioserver.memory;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.BitSet;

import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * @author nagalun
 * @date 07-08-2017
 * Quick and dirty way of checking MemoryManager's buffer usage
 */
public class MemoryView extends JPanel {
	public BitSet bs;
	private BufferedImage bi;
	private JLabel jl;
	private Runtime runtime = Runtime.getRuntime();

	private static final long serialVersionUID = 2645689702783057027L;

	public MemoryView(BitSet bs) {
		this.bs = bs;
        bi = new BufferedImage(1024, 100, BufferedImage.TYPE_INT_RGB);
        ImageIcon icon = new ImageIcon( bi );
        add( new JLabel(icon) );
        jl = new JLabel();
        add(jl);
        render();        
	}
	
	public void render() {
		for (int y = 0; y < 100; y += 5)
        {
            for (int x = 0; x < 1024; x++)
            {
                Color color = bs.get(x + 1024 * (y / 5)) ? (y % 2 == 0) ? Color.RED : Color.GREEN : Color.BLACK;
                int colorValue = color.getRGB();
                bi.setRGB(x, y, colorValue);
                bi.setRGB(x, y + 1, colorValue);
                bi.setRGB(x, y + 2, colorValue);
                bi.setRGB(x, y + 3, colorValue);
                bi.setRGB(x, y + 4, colorValue);
            }
        }
		jl.setText("Used Memory: " 
				+ (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)+ " MB");
		this.repaint();
	}
}
