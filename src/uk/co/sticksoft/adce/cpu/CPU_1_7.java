package uk.co.sticksoft.adce.cpu;

import java.util.LinkedList;
import java.util.Queue;

/*
 * Half-baked 1.7 version of the CPU
 */
public class CPU_1_7 extends CPU
{
	public int A = 0, B = 1, C = 2, X = 3, Y = 4, Z = 5, I = 6, J = 7;
	public char[] register = new char[J+1];	
	public char PC, SP, EX, IA;
	
	// Flags
	private boolean onFire, skipping, interruptQueueing;
	
	// Temporary cache for SP and PC 
	private char tSP, tPC;
	
	// Interrupt queue
	private Queue<Character> interruptQueue = new LinkedList<Character>();

	
	@Override
	public void reset()
	{
		for (int i = 0; i < register.length; i++)
			register[i] = 0;
		for (int i = 0; i < 0x10000; i++)
			RAM[i] = 0;
		PC = SP = EX = IA = 0;
		cycleCount = 0;
		onFire = skipping = interruptQueueing = false;
		interruptQueue.clear();
	}
	
	private enum AddressType
	{
		REGISTER,
		RAM,
		PC,
		SP,
		EX,
		IA,
		LITERAL
	}
	
	private AddressType addressType(char value)
	{
		if (value < 0x08)
			return AddressType.REGISTER;
		if (value < 0x10)
			return AddressType.RAM;
		if (value < 0x18)
			return AddressType.RAM;
		if (value == 0x18)
			return AddressType.RAM;
		if (value == 0x19)
			return AddressType.RAM;
		if (value == 0x1a)
			return AddressType.RAM;
		if (value == 0x1b)
			return AddressType.SP;
		if (value == 0x1c)
			return AddressType.PC;
		if (value == 0x1d)
			return AddressType.EX;
		if (value == 0x1e)
			return AddressType.RAM;
		if (value == 0x1f)
			return AddressType.RAM;
		
		return AddressType.LITERAL;
	}
	
	private char addressA(char value)
	{
		if (value < 0x08)
			return value;
		if (value < 0x10)
			return register[value-0x08];
		if (value < 0x18)
			return (char)(RAM[tPC++]+register[value-0x10]);
		if (value == 0x18)
			return tSP++;
		if (value == 0x19)
			return tSP;
		if (value == 0x1a)
			return (char)(RAM[tPC++] + tSP);
		if (value == 0x1b)
			return 0;
		if (value == 0x1c)
			return 0;
		if (value == 0x1d)
			return 0;
		if (value == 0x1e)
			return RAM[tPC++];
		if (value == 0x1f)
			return tPC++;
		
		return (char)(value - 0x20);
	}
	
	private char addressB(char value)
	{
		if (value < 0x08)
			return value;
		if (value < 0x10)
			return register[value-0x08];
		if (value < 0x18)
			return (char)(RAM[tPC++]+register[value-0x10]);
		if (value == 0x18)
			return --tSP;
		if (value == 0x19)
			return tSP;
		if (value == 0x1a)
			return (char)(RAM[tPC++] + tSP);
		if (value == 0x1b)
			return 0;
		if (value == 0x1c)
			return 0;
		if (value == 0x1d)
			return 0;
		if (value == 0x1e)
			return RAM[tPC++];
		if (value == 0x1f)
			return tPC++;
		
		return (char)(value - 0x20);
	}
	
	private char read(AddressType type, char address)
	{
		switch (type)
		{
		case REGISTER:
			return register[address];
		case RAM:
			return RAM[address];
		case PC:
			return PC;
		case SP:
			return SP;
		case EX:
			return EX;
		default:
			if (address == 0)
				return 0xFFFF;
			else
				return (char)(address-1);
		}
	}
	
	private void write(AddressType type, char address, char word)
	{
		switch (type)
		{
		case REGISTER:
			register[address] = word;
			break;
		case RAM:
			RAM[address] = word;
			break;
		case PC:
			PC = word;
			break;
		case SP:
			SP = word;
			break;
		case EX:
			EX = word;
			break;
		default:
			// What yo playin' at?!
		}
	}
	
	private static int unsignedCharAsSignedInt(char c)
	{
		return c < 0x8000 ? c : c - 0x10000;
	}

	@Override
	public void execute()
	{
		tSP = SP;			// Cache SP
		tPC = (char)(PC+1); // Pre-increment tPC
		
		boolean error = false; // Not sure what to do with this yet (maybe set on fire?)
		
		// Fetch
		char instr = RAM[PC];
		
		// Decode
		int opcode = instr & 0x1f;
		char b = (char)((instr >> 5) & 0x1f);
		char a = (char)(instr >> 10);
		
		if (opcode != 0)
		{
			if (skipping)
			{
				// Skip reading next words 
				if ((b >= 0x10 && b < 0x18) || b == 0x1a || b == 0x1e || b == 0x1f) // [register + next word], [SP + next word], [next word], next word
					tPC++;
				if ((a >= 0x10 && a < 0x18) || a == 0x1a || a == 0x1e || a == 0x1f) // [register + next word], [SP + next word], [next word], next word
					tPC++;
				
				PC = tPC;
				
				if (opcode < 0x10 || opcode > 0x17) // Keep skipping over conditionals
					skipping = false;
				return;
			}
			
			// Work out address type and location before instruction
			// These will only modify tSP or tPC, not the actual SP or PC
			// until after the instruction completes. 
			AddressType aType = addressType(a), bType = addressType(b);
			char aAddr = addressA(a), bAddr = addressB(b);
		
			// Grab the actual values
			char aVal = read(aType, aAddr), bVal = read(bType, bAddr);
			
			int res = 0; // Result
			
			switch (opcode)
			{
				case 0x01: // SET
					res = aVal;
					break;
				case 0x02: // ADD
					res = bVal + aVal;
					EX = (res < 0xffff) ? (char)0 : (char)1;
					break;
				case 0x03: // SUB
					res = bVal - aVal;
					EX = (res > 0) ? 0 : (char)0xffff;
					break;
				case 0x04: // MUL
					res = bVal * aVal;
					EX = (char)(res >> 16);
					break;
				case 0x05: // MLI
					res = unsignedCharAsSignedInt(bVal) * unsignedCharAsSignedInt(aVal);
					EX = (char)(res >> 16);
					break;
				case 0x06: // DIV
					res = bVal / aVal;
					EX = (char)(((bVal << 16) / aVal) & 0xffff);
					break;
				case 0x07: // DVI
					res = unsignedCharAsSignedInt(bVal) / unsignedCharAsSignedInt(aVal);
					EX = (char)(((unsignedCharAsSignedInt(bVal) << 16) / unsignedCharAsSignedInt(aVal)) & 0xffff);
					break;
				case 0x08: // MOD
					if (aVal == 0)
						res = 0;
					else
						res = bVal % aVal;
					break;
				case 0x09: // MDI
					if (aVal == 0)
						res = 0;
					else
						res = unsignedCharAsSignedInt(bVal) % unsignedCharAsSignedInt(aVal);
					break;
				case 0x0a: // AND
					res = bVal & aVal;
					break;
				case 0x0b: // BOR
					res = bVal | aVal;
					break;
				case 0x0c: // XOR
					res = bVal ^ aVal;
					break;
				case 0x0d: // SHR
					res = bVal >>> a;
					EX = (char)(((bVal << 16) >>> a) & 0xffff);
					break;
				case 0x0e: // ASR
					res = bVal >> a;
					EX = (char)(((bVal << 16) >> a) & 0xffff);
					break;
				case 0x0f: // SHL
					res = bVal << a;
					EX = (char)(((bVal << aVal) >> 16) & 0xfff);
					break;
				case 0x10: // IFB
					skipping = (bVal & aVal) != 0;
					break;
				case 0x11: // IFC
					skipping = (bVal & aVal) == 0;
					break;
				case 0x12: // IFE
					skipping = (bVal == aVal);
					break;
				case 0x13: // IFN
					skipping = (bVal != aVal);
					break;
				case 0x14: // IFG
					skipping = (bVal > aVal);
					break;
				case 0x15: // IFA
					skipping = (unsignedCharAsSignedInt(bVal) > unsignedCharAsSignedInt(aVal));
					break;
				case 0x16: // IFL
					skipping = (bVal < aVal);
					break;
				case 0x17: // IFU
					skipping = (unsignedCharAsSignedInt(bVal) < unsignedCharAsSignedInt(bVal));
					break;
				case 0x18: // -
				case 0x19: // -
					error = true;
					break;
				case 0x1a: // ADX
					res = bVal + aVal + EX;
					EX = (char)((res < 0) ? 0 : 1);
					break;
				case 0x1b: // SBX
					res = bVal - aVal - EX;
					EX = (char)((res > 0) ? 0 : 1);
					break;
				case 0x1c: // -
				case 0x1d: // -
					error = true;
					break;
				case 0x1e: // STI
					res = aVal;
					register[I]++;
					register[J]++;
					break;
				case 0x1f: // STD
					res = aVal;
					register[I]--;
					register[J]--;
					break;
			}
			
			write(bType, bAddr, (char)(res & 0xffff));
		}
		else
		{
			if (skipping)
			{
				// Skip reading next word
				if ((a >= 0x10 && a < 0x18) || a == 0x1a || a == 0x1e || a == 0x1f) // [register + next word], [SP + next word], [next word], next word
					tPC++;
				
				skipping = false;
				PC = tPC;
			}
			
			switch (b) // b becomes the special opcode
			{
			case 0x00: // Reserved
				error = true;
				break;
			case 0x01: // JSR
				// Get jump address
				char jmp = read(addressType(a), addressA(a));
				
				// Push PC
				RAM[--tSP] = tPC;
				
				// Set PC to jump address (after increment)
				tPC = jmp;
				break;
			case 0x02: // -
			case 0x03: // -
			case 0x04: // -
			case 0x05: // -
			case 0x06: // -
			case 0x07: // -
				error = true;
				break;
			case 0x08: // INT
				char interrupt = read(addressType(a), addressA(a));
				if (interruptQueueing)
					interruptQueue.add(Character.valueOf(interrupt));
				else if (IA != 0)
				{
					interruptQueueing = true;
					RAM[--tSP] = tPC;
					RAM[--tSP] = register[A];
					tPC = IA;
					register[A] = interrupt;
				}
				break;
			case 0x09: // IAG
				write(addressType(a), addressA(a), IA);
				break;
			case 0x0a: // IAS
				IA = read(addressType(a), addressA(a));
				break;
			case 0x0b: // RFI
				interruptQueueing = false;
				register[A] = RAM[tSP++];
				tPC = RAM[tSP++];
				break;
			case 0x0c: // IAQ
				interruptQueueing = (read(addressType(a), addressA(a)) == 0);
				break;
			case 0x0d: // -
			case 0x0e: // -
			case 0x0f: // -
				error = true;
				break;
			case 0x10: // HWN
			case 0x11: // HWQ
			case 0x12: // HWI
				break;
			case 0x13: // -
			case 0x14: // -
			case 0x15: // -
			case 0x16: // -
			case 0x17: // -
			case 0x18: // -
			case 0x19: // -
			case 0x1a: // -
			case 0x1b: // -
			case 0x1c: // -
			case 0x1d: // -
			case 0x1e: // -
			case 0x1f: // -
				error = true;
				break;
			}
		}
		
		if (!interruptQueueing && !interruptQueue.isEmpty())
		{
			interruptQueueing = true;
			RAM[tSP--] = tPC;
			RAM[tSP--] = register[A];
			tPC = IA;
			register[A] = interruptQueue.remove().charValue();
		}
		
		PC = tPC;
		SP = tSP;
	}

	@Override
	public String getStatusText()
	{
		return String.format(" A:%04x B:%04x C:%04x\n X:%04x Y:%04x Z:%04x\n I:%04x J:%04x\n PC:%04x SP:%04x EX:%04x IA:%04x",
				(int)register[A], (int)register[B], (int)register[C],
				(int)register[X], (int)register[Y], (int)register[Z],
				(int)register[I], (int)register[J],
				(int)PC, (int)SP, (int)EX, (int)IA);
	}

}
