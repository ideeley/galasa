package io.ejat.zos3270.internal.datastream;

import java.nio.ByteBuffer;

public class OrderStartField extends Order {
	
	public static final byte ID = 0x1d;

	private final boolean fieldProtected; 
	private final boolean fieldNumeric; 
	private final boolean fieldDisplay; 
	private final boolean fieldIntenseDisplay; 
	private final boolean fieldSelectorPen; 
	private final boolean fieldModifed; 
	
	public OrderStartField(ByteBuffer buffer) {
		byte attributes = buffer.get();
		this.fieldProtected = ((attributes & 0x20) == 0x20);
		this.fieldNumeric = ((attributes & 0x10) == 0x10);
		this.fieldDisplay = ((attributes & 0x08) == 0x00);
		this.fieldIntenseDisplay = ((attributes & 0x0c) == 0x08);
		this.fieldSelectorPen = (((attributes & 0x0c) == 0x04) || ((attributes & 0x0c) == 0x08));
		this.fieldModifed = ((attributes & 0x01) == 0x01);
		
	} 
	
	
	
	public boolean isFieldProtected() {
		return fieldProtected;
	}



	public boolean isFieldNumeric() {
		return fieldNumeric;
	}



	public boolean isFieldDisplay() {
		return fieldDisplay;
	}



	public boolean isFieldIntenseDisplay() {
		return fieldIntenseDisplay;
	}



	public boolean isFieldSelectorPen() {
		return fieldSelectorPen;
	}



	public boolean isFieldModifed() {
		return fieldModifed;
	}



	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("SF(");
		if (this.fieldProtected) {
			sb.append("Protected ");
		} else {
			sb.append("Unprotected ");
		}
		if (this.fieldNumeric) {
			sb.append("Numeric ");
		} else {
			sb.append("Alphanumeric ");
		}
		
		if (this.fieldDisplay) {
			sb.append("Display ");
		}
		if (this.fieldIntenseDisplay) {
			sb.append("Intense ");
		}
		if (!this.fieldDisplay && !this.fieldIntenseDisplay) {
			sb.append("Nondisplay ");
		}
		if (this.fieldSelectorPen) {
			sb.append("SelectorPen ");
		}
		
		if (this.fieldModifed) {
			sb.append("Modified");
		} else {
			sb.append("Unmodified");
		}
		sb.append(")");
		
		return sb.toString();
	}
	
}
