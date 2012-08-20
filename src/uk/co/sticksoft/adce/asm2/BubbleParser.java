package uk.co.sticksoft.adce.asm2;
import android.graphics.*;
import java.util.*;

public class BubbleParser
{
    private BubbleParser(String source, BubbleView view)
	{
		this.source = source;
		this.view = view;
		this.index = 0;
	}
	
	private String source;
	private BubbleView view;
	private int index;
	
	public static void parse(String source, BubbleView view)
	{
		view.getRoots().clear();
		new BubbleParser(source, view).parse();
	}
	
	private char readNext()
	{
		if (index >= source.length())
			return 0;
		
		return source.charAt(index++);
	}
	
	private void parse()
	{
		char c;
		while (index < source.length())
		{
			c = readNext();
			
			if (c == ':')
				readLabel();
			else if (c == ';')
			    readComment();
			else if (Character.isWhitespace(c))
			    continue;
			else
			    readInstruction(c);
		}
		
		view.layoutBubbles();
		view.invalidate();
	}
	
	private void readLabel()
	{
		StringBuilder sb = new StringBuilder(":");
		
		char c;
		while ((c = readNext()) != 0 && Character.isLetterOrDigit(c))
			sb.append(c);
		
		BubbleNode root = new BubbleNode(sb.toString());
		root.normalColour = Color.rgb(128,64,64);
		view.getRoots().add(root);
	}
	
	private void readComment() // Don't be silly, no-one reads comments :0)
	{
		StringBuilder sb = new StringBuilder(";");

		char c;
		while ((c = readNext()) != 0 && c != '\n' && c != '\r')
			sb.append(c);


		view.getRoots().add(new BubbleNode(sb.toString()));
	}
	
	private void readInstruction(char startsWith)
	{
		StringBuilder sb = new StringBuilder();
		sb.append(startsWith);

		char c;
		while ((c = readNext()) != 0 && Character.isWhitespace(c))
		{}
		index--;
		while ((c = readNext()) != 0 && !Character.isWhitespace(c))
			sb.append(c);


		BubbleNode root = new BubbleNode(sb.toString());
		root.normalColour = Color.rgb(64,64,128);
		view.getRoots().add(root);
		
		if (c != 0)
			readParam(root);
	}
	
	private void readParam(BubbleNode node)
	{
		StringBuilder sb = new StringBuilder();

		char c;
		while ((c = readNext()) != 0 && Character.isWhitespace(c))
		{}
		index--;
		while ((c = readNext()) != 0 && !Character.isWhitespace(c) && c != ',')
			sb.append(c);


		BubbleNode root = new BubbleNode(sb.toString());
		root.normalColour = Color.rgb(192,64,64);
		node.addProperty(root);
		
		if (c == ',')
			readParam(node);
	}
	
	public static String unparse(BubbleView view)
	{
		StringBuilder builder = new StringBuilder();
		
		ArrayList<BubbleNode> roots = view.getRoots();
		for (int i = 0; i < roots.size(); i++)
		{
			BubbleNode node = roots.get(i);
			builder.append(node.text());
			
			for (int j = 0; j < node.properties.size(); j++)
			{
				builder.append((j == 0) ? " " : ", ");
				builder.append(node.children.get(j));
			}
			
			builder.append("\n");
		}
		
		return builder.toString();
	}
}
