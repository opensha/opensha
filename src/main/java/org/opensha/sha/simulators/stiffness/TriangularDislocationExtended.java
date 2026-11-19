package org.opensha.sha.simulators.stiffness;

/**
 * Fixed extended-precision evaluation of the triangle free-surface correction.
 * Uses the same equations as TriangularDislocationPrecision, with two-double
 * arithmetic and shared subexpressions instead of arbitrary-precision decimals.
 * Generated algebra is kept in small methods so HotSpot can compile it normally.
 * Regenerate/check it with the test-resource regenerate_triangle_precision.py tool.
 */
/*
MIT License

Copyright (c) 2021 T. Ben Thompson

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
 */
final class TriangularDislocationExtended {
	private static final DoubleDouble PI_EXTENDED = DoubleDouble
			.parse("3.14159265358979323846264338327950288419716939937510582097494459");
	private static final DoubleDouble M_PI = number("3.14159265358979323846264338327950288419716939937510582097494459");

	private TriangularDislocationExtended() {
	}

	/** Difference of the two endpoint corrections, in the edge's angular coordinates. */
	static double[] edge(double[] observation, double[] slip, double[] start, double[] end, double poisson) {
		DoubleDouble sx = subtract(value(end[0]), value(start[0]));
		DoubleDouble sy = subtract(value(end[1]), value(start[1]));
		DoubleDouble sz = subtract(value(end[2]), value(start[2]));
		DoubleDouble horizontal = sqrt(add(multiply(sx, sx), multiply(sy, sy)));
		DoubleDouble length = sqrt(add(multiply(horizontal, horizontal), multiply(sz, sz)));
		DoubleDouble dx = subtract(value(observation[0]), value(start[0]));
		DoubleDouble dy = subtract(value(observation[1]), value(start[1]));
		DoubleDouble dz = subtract(value(start[2]), value(observation[2]));
		DoubleDouble y1 = divide(add(multiply(sx, dx), multiply(sy, dy)), horizontal);
		DoubleDouble y2 = divide(subtract(multiply(sy, dx), multiply(sx, dy)), horizontal);
		DoubleDouble b1 = divide(add(multiply(sx, value(slip[0])), multiply(sy, value(slip[1]))), horizontal);
		DoubleDouble b2 = divide(subtract(multiply(sy, value(slip[0])), multiply(sx, value(slip[1]))), horizontal);
		DoubleDouble b3 = value(-slip[2]);
		DoubleDouble sinB = divide(horizontal, length), cosB = divide(negate(sz), length);
		if (y1.signum() >= 0) {
			sinB = negate(sinB);
			cosB = negate(cosB);
		}
		DoubleDouble[] first = strain(y1, y2, dz, sinB, cosB, b1, b2, b3, value(poisson), value(-start[2]));
		DoubleDouble[] second = strain(subtract(y1, horizontal), y2, add(dz, sz), sinB, cosB,
				b1, b2, b3, value(poisson), value(-end[2]));
		double[] result = new double[6];
		for (int i = 0; i < 6; i++)
			result[i] = subtract(second[i], first[i]).doubleValue();
		return result;
	}

	// @formatter:off — generated algebra uses fixed continuation indentation.
	private static DoubleDouble[] strain(DoubleDouble y1, DoubleDouble y2, DoubleDouble y3, DoubleDouble sinB,
			DoubleDouble cosB, DoubleDouble b1, DoubleDouble b2, DoubleDouble b3, DoubleDouble nu, DoubleDouble a) {
		Terms t = new Terms(y1, y2, y3, sinB, cosB, b1, b2, b3, nu, a);
		return new DoubleDouble[] {componentXX(t), componentYY(t), componentZZ(t), componentXY(t), componentXZ(t),
				componentYZ(t)};
	}
	private static final class Terms {
		final DoubleDouble y1, y2, y3, sinB, cosB, b1, b2, b3, nu, a, cotB, y3b, z1b, z3b, rb2, rb, W1, W2, W3, W4, W5,
				W6, W7, W8, W9, N1, rFib_ry2, rFib_ry1, rFib_ry3;
		Terms(DoubleDouble y1, DoubleDouble y2, DoubleDouble y3, DoubleDouble sinB, DoubleDouble cosB, DoubleDouble b1,
				DoubleDouble b2, DoubleDouble b3, DoubleDouble nu, DoubleDouble a) {
			this.y1 = y1;
			this.y2 = y2;
			this.y3 = y3;
			this.sinB = sinB;
			this.cosB = cosB;
			this.b1 = b1;
			this.b2 = b2;
			this.b3 = b3;
			this.nu = nu;
			this.a = a;
			cotB = divide(cosB, sinB);
			y3b = add(y3, multiply(number("2"), a));
			z1b = add(multiply(y1, cosB), multiply(y3b, sinB));
			z3b = add(multiply(negate(y1), sinB), multiply(y3b, cosB));
			rb2 = add(add(multiply(y1, y1), multiply(y2, y2)), multiply(y3b, y3b));
			rb = sqrt(rb2);
			W1 = add(multiply(rb, cosB), y3b);
			W2 = add(cosB, divide(a, rb));
			W3 = add(cosB, divide(y3b, rb));
			W4 = add(nu, divide(a, rb));
			W5 = add(multiply(number("2"), nu), divide(a, rb));
			W6 = add(rb, y3b);
			W7 = add(rb, z3b);
			W8 = add(y3, a);
			W9 = add(number("1"), divide(divide(a, rb), cosB));
			N1 = subtract(number("1"), multiply(number("2"), nu));
			rFib_ry2 = subtract(divide(divide(z1b, rb), add(rb, z3b)), divide(divide(y1, rb), add(rb, y3b)));
			rFib_ry1 = subtract(divide(divide(y2, rb), add(rb, y3b)), divide(divide(multiply(cosB, y2), rb), add(rb,
					z3b)));
			rFib_ry3 = divide(divide(multiply(negate(sinB), y2), rb), add(rb, z3b));
			common = new DoubleDouble[627];
			init0(this);
			init40(this);
			init80(this);
			init120(this);
			init160(this);
			init200(this);
			init240(this);
			init280(this);
			init320(this);
			init360(this);
			init400(this);
			init440(this);
			init480(this);
			init520(this);
			init560(this);
			init600(this);
		}
		final DoubleDouble[] common;
	}
	private static void init0(Terms t) {
		t.common[0] = divide(number("1.0"), number("4.0"));
		t.common[1] = negate(number("2"));
		t.common[2] = multiply(number("2"), t.nu);
		t.common[3] = add(t.common[1], t.common[2]);
		t.common[4] = multiply(t.common[3], t.N1);
		t.common[5] = multiply(t.cotB, t.cotB);
		t.common[6] = multiply(t.N1, t.y2);
		t.common[7] = multiply(t.W6, t.W6);
		t.common[8] = divide(t.common[6], t.common[7]);
		t.common[9] = subtract(number("1"), t.W5);
		t.common[10] = multiply(t.common[9], t.cotB);
		t.common[11] = divide(t.y1, t.W6);
		t.common[12] = multiply(t.common[11], t.W4);
		t.common[13] = subtract(t.common[10], t.common[12]);
		t.common[14] = multiply(t.common[8], t.common[13]);
		t.common[15] = divide(t.common[6], t.W6);
		t.common[16] = multiply(t.rb, t.rb);
		t.common[17] = multiply(t.common[16], t.rb);
		t.common[18] = divide(t.a, t.common[17]);
		t.common[19] = multiply(t.common[18], t.y1);
		t.common[20] = multiply(t.common[19], t.cotB);
		t.common[21] = divide(number("1"), t.W6);
		t.common[22] = multiply(t.common[21], t.W4);
		t.common[23] = multiply(t.y1, t.y1);
		t.common[24] = divide(t.common[23], t.common[7]);
		t.common[25] = multiply(t.common[24], t.W4);
		t.common[26] = divide(t.common[25], t.rb);
		t.common[27] = divide(t.common[23], t.W6);
		t.common[28] = multiply(t.common[27], t.a);
		t.common[29] = divide(t.common[28], t.common[17]);
		t.common[30] = multiply(t.common[6], t.cosB);
		t.common[31] = multiply(t.common[30], t.cotB);
		t.common[32] = multiply(t.W7, t.W7);
		t.common[33] = divide(t.common[31], t.common[32]);
		t.common[34] = multiply(t.common[33], t.W2);
		t.common[35] = divide(t.y1, t.rb);
		t.common[36] = subtract(t.common[35], t.sinB);
		t.common[37] = multiply(number("3"), t.a);
		t.common[38] = multiply(t.common[37], t.y2);
		t.common[39] = multiply(t.common[38], t.W8);
	}
	private static void init40(Terms t) {
		t.common[40] = multiply(t.common[39], t.cotB);
		t.common[41] = multiply(t.rb, t.rb2);
		t.common[42] = multiply(t.common[41], t.rb2);
		t.common[43] = divide(t.common[40], t.common[42]);
		t.common[44] = multiply(t.common[43], t.y1);
		t.common[45] = multiply(t.y2, t.W8);
		t.common[46] = divide(t.common[45], t.common[17]);
		t.common[47] = divide(t.common[46], t.W6);
		t.common[48] = negate(t.N1);
		t.common[49] = multiply(t.common[48], t.cotB);
		t.common[50] = multiply(t.common[11], t.W5);
		t.common[51] = add(t.common[49], t.common[50]);
		t.common[52] = multiply(t.a, t.y1);
		t.common[53] = divide(t.common[52], t.rb2);
		t.common[54] = add(t.common[51], t.common[53]);
		t.common[55] = divide(t.common[45], t.rb2);
		t.common[56] = divide(t.common[55], t.common[7]);
		t.common[57] = divide(t.common[45], t.rb);
		t.common[58] = divide(t.common[57], t.W6);
		t.common[59] = multiply(t.common[21], t.W5);
		t.common[60] = divide(t.a, t.rb2);
		t.common[61] = multiply(number("2"), t.a);
		t.common[62] = multiply(t.common[61], t.common[23]);
		t.common[63] = multiply(t.rb2, t.rb2);
		t.common[64] = divide(t.common[62], t.common[63]);
		t.common[65] = divide(t.cosB, t.W7);
		t.common[66] = multiply(t.N1, t.cosB);
		t.common[67] = divide(t.a, t.rb);
		t.common[68] = subtract(t.common[66], t.common[67]);
		t.common[69] = multiply(t.W1, t.common[68]);
		t.common[70] = multiply(t.common[69], t.cotB);
		t.common[71] = subtract(number("2"), t.common[2]);
		t.common[72] = multiply(t.rb, t.sinB);
		t.common[73] = subtract(t.common[72], t.y1);
		t.common[74] = multiply(t.common[71], t.common[73]);
		t.common[75] = multiply(t.common[74], t.cosB);
		t.common[76] = add(t.common[70], t.common[75]);
		t.common[77] = multiply(t.common[65], t.common[76]);
		t.common[78] = multiply(t.a, t.y3b);
		t.common[79] = multiply(t.common[78], t.cosB);
	}
	private static void init80(Terms t) {
		t.common[80] = multiply(t.common[79], t.cotB);
		t.common[81] = divide(t.common[80], t.rb2);
		t.common[82] = subtract(t.common[77], t.common[81]);
		t.common[83] = divide(t.common[57], t.common[32]);
		t.common[84] = multiply(t.common[83], t.common[82]);
		t.common[85] = divide(t.common[57], t.W7);
		t.common[86] = negate(t.cosB);
		t.common[87] = divide(t.common[86], t.common[32]);
		t.common[88] = multiply(t.common[87], t.common[76]);
		t.common[89] = divide(number("1"), t.rb);
		t.common[90] = multiply(t.common[89], t.cosB);
		t.common[91] = multiply(t.common[90], t.y1);
		t.common[92] = multiply(t.W1, t.a);
		t.common[93] = multiply(t.common[89], t.sinB);
		t.common[94] = multiply(t.common[93], t.y1);
		t.common[95] = subtract(t.common[94], number("1"));
		t.common[96] = multiply(t.common[61], t.y3b);
		t.common[97] = multiply(t.common[96], t.cosB);
		t.common[98] = multiply(t.common[97], t.cotB);
		t.common[99] = divide(t.common[98], t.common[63]);
		t.common[100] = subtract(number("1"), t.nu);
		t.common[101] = multiply(t.common[71], t.common[5]);
		t.common[102] = add(t.common[101], t.nu);
		t.common[103] = divide(t.common[102], t.rb);
		t.common[104] = add(t.common[101], number("1"));
		t.common[105] = multiply(t.common[104], t.cosB);
		t.common[106] = divide(t.N1, t.common[7]);
		t.common[107] = multiply(t.common[48], t.y1);
		t.common[108] = multiply(t.common[107], t.cotB);
		t.common[109] = multiply(t.nu, t.y3b);
		t.common[110] = add(t.common[108], t.common[109]);
		t.common[111] = subtract(t.common[110], t.a);
		t.common[112] = multiply(t.common[52], t.cotB);
		t.common[113] = divide(t.common[112], t.rb);
		t.common[114] = add(t.common[111], t.common[113]);
		t.common[115] = multiply(t.common[27], t.W4);
		t.common[116] = add(t.common[114], t.common[115]);
		t.common[117] = multiply(t.common[106], t.common[116]);
		t.common[118] = divide(t.common[117], t.rb);
		t.common[119] = divide(t.N1, t.W6);
	}
	private static void init120(Terms t) {
		t.common[120] = multiply(t.a, t.common[23]);
		t.common[121] = multiply(t.common[120], t.cotB);
		t.common[122] = divide(t.common[121], t.common[17]);
		t.common[123] = multiply(number("2"), t.y1);
		t.common[124] = multiply(t.common[23], t.y1);
		t.common[125] = multiply(t.N1, t.cotB);
		t.common[126] = divide(t.common[125], t.common[32]);
		t.common[127] = multiply(t.z1b, t.cosB);
		t.common[128] = multiply(t.a, t.common[73]);
		t.common[129] = divide(t.common[128], t.rb);
		t.common[130] = divide(t.common[129], t.cosB);
		t.common[131] = subtract(t.common[127], t.common[130]);
		t.common[132] = multiply(t.common[126], t.common[131]);
		t.common[133] = divide(t.common[125], t.W7);
		t.common[134] = multiply(t.cosB, t.cosB);
		t.common[135] = divide(t.common[128], t.common[17]);
		t.common[136] = divide(t.common[135], t.cosB);
		t.common[137] = multiply(t.a, t.W8);
		t.common[138] = multiply(t.common[137], t.cotB);
		t.common[139] = divide(t.common[138], t.common[17]);
		t.common[140] = multiply(t.common[37], t.common[23]);
		t.common[141] = multiply(t.common[140], t.W8);
		t.common[142] = multiply(t.common[141], t.cotB);
		t.common[143] = divide(t.common[142], t.common[42]);
		t.common[144] = divide(t.W8, t.common[7]);
		t.common[145] = multiply(t.N1, t.y1);
		t.common[146] = multiply(t.common[145], t.cotB);
		t.common[147] = add(t.common[146], t.a);
		t.common[148] = multiply(t.common[89], t.common[147]);
		t.common[149] = add(t.common[2], t.common[148]);
		t.common[150] = divide(t.common[23], t.rb);
		t.common[151] = divide(t.common[150], t.W6);
		t.common[152] = multiply(t.common[151], t.W5);
		t.common[153] = subtract(t.common[149], t.common[152]);
		t.common[154] = divide(t.common[120], t.common[17]);
		t.common[155] = subtract(t.common[153], t.common[154]);
		t.common[156] = multiply(t.common[144], t.common[155]);
		t.common[157] = divide(t.common[156], t.rb);
		t.common[158] = divide(t.W8, t.W6);
		t.common[159] = negate(number("1"));
	}
	private static void init160(Terms t) {
		t.common[160] = divide(t.common[159], t.common[17]);
		t.common[161] = multiply(t.common[160], t.common[147]);
		t.common[162] = multiply(t.common[89], t.N1);
		t.common[163] = multiply(t.common[162], t.cotB);
		t.common[164] = divide(t.common[61], t.common[17]);
		t.common[165] = multiply(t.W8, t.cotB);
		t.common[166] = divide(t.common[165], t.common[32]);
		t.common[167] = multiply(t.common[86], t.sinB);
		t.common[168] = multiply(t.common[52], t.y3b);
		t.common[169] = divide(t.common[168], t.common[17]);
		t.common[170] = divide(t.common[169], t.cosB);
		t.common[171] = add(t.common[167], t.common[170]);
		t.common[172] = divide(t.common[73], t.rb);
		t.common[173] = multiply(t.common[71], t.cosB);
		t.common[174] = divide(t.W1, t.W7);
		t.common[175] = multiply(t.common[174], t.W9);
		t.common[176] = subtract(t.common[173], t.common[175]);
		t.common[177] = multiply(t.common[172], t.common[176]);
		t.common[178] = add(t.common[171], t.common[177]);
		t.common[179] = multiply(t.common[166], t.common[178]);
		t.common[180] = divide(t.common[165], t.W7);
		t.common[181] = divide(t.common[73], t.common[17]);
		t.common[182] = multiply(t.common[181], t.common[176]);
		t.common[183] = divide(t.common[159], t.rb);
		t.common[184] = multiply(t.common[183], t.cosB);
		t.common[185] = multiply(t.common[184], t.y1);
		t.common[186] = divide(t.common[185], t.W7);
		t.common[187] = divide(t.W1, t.common[32]);
		t.common[188] = multiply(t.common[187], t.W9);
		t.common[189] = multiply(t.common[188], t.common[36]);
		t.common[190] = multiply(t.common[174], t.a);
		t.common[191] = divide(t.common[190], t.common[17]);
		t.common[192] = divide(t.common[191], t.cosB);
		t.common[193] = multiply(t.common[192], t.y1);
		t.common[194] = negate(t.y2);
		t.common[195] = divide(t.common[194], t.common[7]);
		t.common[196] = add(number("1"), t.common[67]);
		t.common[197] = multiply(t.common[195], t.common[196]);
		t.common[198] = divide(t.y2, t.W6);
		t.common[199] = multiply(t.common[198], t.a);
	}
	private static void init200(Terms t) {
		t.common[200] = divide(t.common[199], t.common[17]);
		t.common[201] = multiply(t.common[200], t.y1);
		t.common[202] = multiply(t.y2, t.cosB);
		t.common[203] = divide(t.common[202], t.common[32]);
		t.common[204] = multiply(t.common[203], t.W2);
		t.common[205] = multiply(t.common[204], t.common[36]);
		t.common[206] = divide(t.common[202], t.W7);
		t.common[207] = multiply(t.common[206], t.a);
		t.common[208] = add(t.common[60], t.common[21]);
		t.common[209] = multiply(t.common[46], t.common[208]);
		t.common[210] = multiply(t.common[209], t.y1);
		t.common[211] = multiply(t.common[1], t.a);
		t.common[212] = divide(t.common[211], t.common[63]);
		t.common[213] = multiply(t.common[212], t.y1);
		t.common[214] = divide(number("1"), t.common[7]);
		t.common[215] = divide(t.common[214], t.rb);
		t.common[216] = multiply(t.common[215], t.y1);
		t.common[217] = subtract(t.common[213], t.common[216]);
		t.common[218] = multiply(t.common[45], t.cosB);
		t.common[219] = divide(t.common[218], t.common[17]);
		t.common[220] = divide(t.common[219], t.W7);
		t.common[221] = multiply(t.common[174], t.W2);
		t.common[222] = divide(t.common[78], t.rb2);
		t.common[223] = add(t.common[221], t.common[222]);
		t.common[224] = divide(t.common[218], t.rb);
		t.common[225] = divide(t.common[224], t.common[32]);
		t.common[226] = multiply(t.common[225], t.common[223]);
		t.common[227] = divide(t.common[224], t.W7);
		t.common[228] = divide(t.common[91], t.W7);
		t.common[229] = multiply(t.common[228], t.W2);
		t.common[230] = multiply(t.common[187], t.W2);
		t.common[231] = multiply(t.common[230], t.common[36]);
		t.common[232] = subtract(t.common[229], t.common[231]);
		t.common[233] = multiply(t.common[191], t.y1);
		t.common[234] = subtract(t.common[232], t.common[233]);
		t.common[235] = divide(t.common[96], t.common[63]);
		t.common[236] = multiply(t.common[235], t.y1);
		t.common[237] = subtract(t.common[234], t.common[236]);
		t.common[238] = subtract(t.common[101], t.nu);
		t.common[239] = divide(t.common[238], t.rb);
	}
	private static void init240(Terms t) {
		t.common[240] = subtract(t.common[104], t.common[2]);
		t.common[241] = multiply(t.common[240], t.cosB);
		t.common[242] = multiply(t.y1, t.cotB);
		t.common[243] = multiply(t.common[242], t.common[9]);
		t.common[244] = add(t.common[243], t.common[109]);
		t.common[245] = subtract(t.common[244], t.a);
		t.common[246] = multiply(t.y2, t.y2);
		t.common[247] = divide(t.common[246], t.W6);
		t.common[248] = multiply(t.common[247], t.W4);
		t.common[249] = add(t.common[245], t.common[248]);
		t.common[250] = multiply(t.common[106], t.common[249]);
		t.common[251] = divide(t.common[250], t.rb);
		t.common[252] = multiply(number("2"), t.y2);
		t.common[253] = multiply(t.common[246], t.y2);
		t.common[254] = multiply(t.N1, t.z1b);
		t.common[255] = multiply(t.common[254], t.cotB);
		t.common[256] = divide(t.common[255], t.common[32]);
		t.common[257] = multiply(t.common[256], t.W2);
		t.common[258] = divide(t.common[255], t.W7);
		t.common[259] = multiply(t.common[258], t.a);
		t.common[260] = multiply(t.common[1], t.nu);
		t.common[261] = subtract(t.common[146], t.a);
		t.common[262] = multiply(t.common[89], t.common[261]);
		t.common[263] = add(t.common[260], t.common[262]);
		t.common[264] = divide(t.common[246], t.rb);
		t.common[265] = divide(t.common[264], t.W6);
		t.common[266] = multiply(t.common[265], t.W5);
		t.common[267] = add(t.common[263], t.common[266]);
		t.common[268] = multiply(t.a, t.common[246]);
		t.common[269] = divide(t.common[268], t.common[17]);
		t.common[270] = add(t.common[267], t.common[269]);
		t.common[271] = multiply(t.common[144], t.common[270]);
		t.common[272] = divide(t.common[271], t.rb);
		t.common[273] = multiply(t.common[160], t.common[261]);
		t.common[274] = divide(t.W8, t.common[32]);
		t.common[275] = multiply(t.a, t.cosB);
		t.common[276] = add(t.common[255], t.common[275]);
		t.common[277] = multiply(t.common[89], t.common[276]);
		t.common[278] = subtract(t.common[134], t.common[277]);
		t.common[279] = multiply(t.common[78], t.z1b);
	}
	private static void init280(Terms t) {
		t.common[280] = multiply(t.common[279], t.cotB);
		t.common[281] = divide(t.common[280], t.common[17]);
		t.common[282] = add(t.common[278], t.common[281]);
		t.common[283] = divide(t.common[89], t.W7);
		t.common[284] = multiply(t.common[246], t.common[134]);
		t.common[285] = multiply(t.a, t.z1b);
		t.common[286] = multiply(t.common[285], t.cotB);
		t.common[287] = divide(t.common[286], t.rb);
		t.common[288] = multiply(t.common[287], t.W1);
		t.common[289] = subtract(t.common[284], t.common[288]);
		t.common[290] = multiply(t.common[283], t.common[289]);
		t.common[291] = subtract(t.common[282], t.common[290]);
		t.common[292] = multiply(t.common[274], t.common[291]);
		t.common[293] = divide(t.W8, t.W7);
		t.common[294] = divide(number("1"), t.common[17]);
		t.common[295] = multiply(t.common[294], t.common[276]);
		t.common[296] = multiply(t.common[37], t.y3b);
		t.common[297] = multiply(t.common[296], t.z1b);
		t.common[298] = multiply(t.common[297], t.cotB);
		t.common[299] = divide(t.common[298], t.common[42]);
		t.common[300] = divide(t.common[294], t.W7);
		t.common[301] = multiply(t.common[300], t.common[289]);
		t.common[302] = divide(number("1"), t.rb2);
		t.common[303] = divide(t.common[302], t.common[32]);
		t.common[304] = divide(t.common[286], t.common[17]);
		t.common[305] = multiply(t.common[304], t.W1);
		t.common[306] = divide(t.common[286], t.rb2);
		t.common[307] = multiply(t.common[306], t.cosB);
		t.common[308] = multiply(t.common[71], t.N1);
		t.common[309] = subtract(t.W5, number("1"));
		t.common[310] = multiply(t.common[309], t.cotB);
		t.common[311] = add(t.common[310], t.common[12]);
		t.common[312] = multiply(t.N1, t.common[246]);
		t.common[313] = divide(t.common[312], t.common[7]);
		t.common[314] = negate(t.a);
		t.common[315] = divide(t.common[314], t.common[17]);
		t.common[316] = divide(t.y1, t.common[7]);
		t.common[317] = multiply(t.common[316], t.W4);
		t.common[318] = divide(t.common[317], t.rb);
		t.common[319] = multiply(t.common[318], t.y2);
	}
	private static void init320(Terms t) {
		t.common[320] = multiply(t.common[312], t.cotB);
		t.common[321] = multiply(t.common[37], t.common[246]);
		t.common[322] = multiply(t.common[321], t.W8);
		t.common[323] = multiply(t.common[322], t.cotB);
		t.common[324] = divide(t.common[323], t.common[42]);
		t.common[325] = divide(t.W8, t.rb);
		t.common[326] = divide(t.common[325], t.W6);
		t.common[327] = multiply(t.common[2], t.y1);
		t.common[328] = divide(t.common[327], t.W6);
		t.common[329] = subtract(t.common[125], t.common[328]);
		t.common[330] = divide(t.common[52], t.rb);
		t.common[331] = add(t.common[89], t.common[21]);
		t.common[332] = multiply(t.common[330], t.common[331]);
		t.common[333] = subtract(t.common[329], t.common[332]);
		t.common[334] = multiply(t.common[246], t.W8);
		t.common[335] = divide(t.common[334], t.common[17]);
		t.common[336] = divide(t.common[335], t.W6);
		t.common[337] = divide(t.common[334], t.rb2);
		t.common[338] = divide(t.common[337], t.common[7]);
		t.common[339] = divide(t.common[327], t.common[7]);
		t.common[340] = divide(t.common[339], t.rb);
		t.common[341] = multiply(t.common[340], t.y2);
		t.common[342] = multiply(t.common[215], t.y2);
		t.common[343] = multiply(t.common[3], t.cosB);
		t.common[344] = add(t.common[343], t.common[175]);
		t.common[345] = divide(t.common[222], t.cosB);
		t.common[346] = add(t.common[344], t.common[345]);
		t.common[347] = multiply(t.common[334], t.cotB);
		t.common[348] = multiply(t.common[45], t.cotB);
		t.common[349] = divide(t.common[348], t.rb);
		t.common[350] = divide(t.common[349], t.W7);
		t.common[351] = multiply(t.common[90], t.y2);
		t.common[352] = divide(t.common[351], t.W7);
		t.common[353] = divide(t.common[188], t.rb);
		t.common[354] = multiply(t.common[353], t.y2);
		t.common[355] = multiply(t.common[192], t.y2);
		t.common[356] = divide(t.common[235], t.cosB);
		t.common[357] = negate(t.sinB);
		t.common[358] = divide(t.y2, t.common[7]);
		t.common[359] = divide(t.z1b, t.common[32]);
	}
	private static void init360(Terms t) {
		t.common[360] = multiply(t.common[359], t.W2);
		t.common[361] = divide(t.z1b, t.W7);
		t.common[362] = multiply(t.common[361], t.a);
		t.common[363] = divide(t.common[362], t.common[17]);
		t.common[364] = multiply(t.y1, t.W8);
		t.common[365] = divide(t.common[364], t.rb);
		t.common[366] = multiply(t.common[212], t.y2);
		t.common[367] = subtract(t.common[366], t.common[342]);
		t.common[368] = subtract(t.cosB, t.common[67]);
		t.common[369] = multiply(t.sinB, t.common[368]);
		t.common[370] = divide(t.z1b, t.rb);
		t.common[371] = add(number("1"), t.common[222]);
		t.common[372] = multiply(t.common[370], t.common[371]);
		t.common[373] = add(t.common[369], t.common[372]);
		t.common[374] = multiply(t.common[246], t.cosB);
		t.common[375] = multiply(t.common[374], t.sinB);
		t.common[376] = divide(t.common[285], t.rb);
		t.common[377] = multiply(t.common[376], t.W1);
		t.common[378] = subtract(t.common[375], t.common[377]);
		t.common[379] = multiply(t.common[283], t.common[378]);
		t.common[380] = subtract(t.common[373], t.common[379]);
		t.common[381] = multiply(t.common[274], t.common[380]);
		t.common[382] = multiply(t.sinB, t.a);
		t.common[383] = divide(t.common[382], t.common[17]);
		t.common[384] = divide(t.z1b, t.common[17]);
		t.common[385] = multiply(t.common[384], t.common[371]);
		t.common[386] = multiply(number("2"), t.z1b);
		t.common[387] = divide(t.common[386], t.common[42]);
		t.common[388] = multiply(t.common[387], t.a);
		t.common[389] = multiply(t.common[388], t.y3b);
		t.common[390] = multiply(t.common[300], t.common[378]);
		t.common[391] = divide(t.common[285], t.common[17]);
		t.common[392] = multiply(t.common[391], t.W1);
		t.common[393] = divide(t.common[285], t.rb2);
		t.common[394] = multiply(t.common[393], t.cosB);
		t.common[395] = divide(t.y3b, t.rb);
		t.common[396] = add(t.common[395], number("1"));
		t.common[397] = divide(number("1.0"), number("2.0"));
		t.common[398] = multiply(t.common[397], t.y2);
		t.common[399] = divide(t.common[398], t.W6);
	}
	private static void init400(Terms t) {
		t.common[400] = multiply(t.common[399], t.a);
		t.common[401] = divide(t.common[400], t.common[17]);
		t.common[402] = multiply(t.common[401], number("2"));
		t.common[403] = multiply(t.common[402], t.y3b);
		t.common[404] = multiply(t.common[204], t.W3);
		t.common[405] = multiply(t.common[398], t.cosB);
		t.common[406] = divide(t.common[405], t.W7);
		t.common[407] = multiply(t.common[406], t.a);
		t.common[408] = divide(t.common[407], t.common[17]);
		t.common[409] = multiply(t.common[408], number("2"));
		t.common[410] = multiply(t.common[409], t.y3b);
		t.common[411] = divide(t.y2, t.rb);
		t.common[412] = divide(t.common[2], t.W6);
		t.common[413] = add(t.common[412], t.common[60]);
		t.common[414] = multiply(t.common[398], t.W8);
		t.common[415] = divide(t.common[414], t.common[17]);
		t.common[416] = divide(t.common[260], t.common[7]);
		t.common[417] = divide(t.common[202], t.rb);
		t.common[418] = divide(t.common[417], t.W7);
		t.common[419] = subtract(number("1"), t.common[2]);
		t.common[420] = subtract(t.common[419], t.common[221]);
		t.common[421] = subtract(t.common[420], t.common[222]);
		t.common[422] = multiply(t.common[414], t.cosB);
		t.common[423] = divide(t.common[422], t.common[17]);
		t.common[424] = divide(t.common[423], t.W7);
		t.common[425] = multiply(t.common[225], t.common[421]);
		t.common[426] = multiply(t.cosB, t.y3b);
		t.common[427] = divide(t.common[426], t.rb);
		t.common[428] = add(t.common[427], number("1"));
		t.common[429] = negate(t.common[428]);
		t.common[430] = divide(t.common[429], t.W7);
		t.common[431] = multiply(t.common[230], t.W3);
		t.common[432] = multiply(t.common[397], t.W1);
		t.common[433] = divide(t.common[432], t.W7);
		t.common[434] = multiply(t.common[433], t.a);
		t.common[435] = divide(t.common[434], t.common[17]);
		t.common[436] = multiply(t.common[435], number("2"));
		t.common[437] = multiply(t.common[436], t.y3b);
		t.common[438] = divide(t.common[78], t.common[63]);
		t.common[439] = multiply(t.common[438], number("2"));
	}
	private static void init440(Terms t) {
		t.common[440] = multiply(t.common[439], t.y3b);
		t.common[441] = multiply(t.common[4], t.cotB);
		t.common[442] = multiply(t.common[71], t.y1);
		t.common[443] = divide(t.common[442], t.common[7]);
		t.common[444] = multiply(t.common[443], t.W5);
		t.common[445] = multiply(t.common[397], t.common[71]);
		t.common[446] = multiply(t.common[71], t.sinB);
		t.common[447] = divide(t.common[446], t.W7);
		t.common[448] = multiply(t.common[447], t.W2);
		t.common[449] = multiply(t.common[71], t.z1b);
		t.common[450] = divide(t.common[449], t.common[32]);
		t.common[451] = multiply(t.common[450], t.W2);
		t.common[452] = subtract(t.common[329], t.common[53]);
		t.common[453] = multiply(t.common[339], t.common[396]);
		t.common[454] = divide(t.common[52], t.common[63]);
		t.common[455] = multiply(t.common[454], number("2"));
		t.common[456] = multiply(t.common[455], t.y3b);
		t.common[457] = divide(number("1"), t.W7);
		t.common[458] = multiply(t.cosB, t.sinB);
		t.common[459] = multiply(t.W1, t.cotB);
		t.common[460] = divide(t.common[459], t.rb);
		t.common[461] = subtract(t.common[173], t.common[174]);
		t.common[462] = multiply(t.common[460], t.common[461]);
		t.common[463] = add(t.common[458], t.common[462]);
		t.common[464] = multiply(t.y3b, t.z1b);
		t.common[465] = divide(t.common[464], t.rb2);
		t.common[466] = subtract(t.sinB, t.common[465]);
		t.common[467] = multiply(t.z1b, t.W1);
		t.common[468] = divide(t.common[467], t.rb);
		t.common[469] = divide(t.common[468], t.W7);
		t.common[470] = subtract(t.common[466], t.common[469]);
		t.common[471] = multiply(t.common[67], t.common[470]);
		t.common[472] = add(t.common[463], t.common[471]);
		t.common[473] = multiply(t.common[274], t.common[472]);
		t.common[474] = multiply(t.common[397], t.a);
		t.common[475] = divide(t.common[474], t.common[17]);
		t.common[476] = multiply(t.common[397], t.z1b);
		t.common[477] = divide(t.common[468], t.common[32]);
		t.common[478] = multiply(t.common[71], t.y2);
		t.common[479] = multiply(t.common[478], t.sinB);
	}
	private static void init480(Terms t) {
		t.common[480] = divide(t.common[479], t.common[32]);
		t.common[481] = multiply(t.common[480], t.W2);
		t.common[482] = add(number("1"), t.common[221]);
		t.common[483] = add(t.common[482], t.common[222]);
		t.common[484] = multiply(t.common[45], t.sinB);
		t.common[485] = divide(t.common[484], t.rb);
		t.common[486] = divide(t.common[485], t.common[32]);
		t.common[487] = multiply(t.common[486], t.common[483]);
		t.common[488] = divide(t.common[485], t.W7);
		t.common[489] = divide(t.common[428], t.W7);
		t.common[490] = multiply(t.common[489], t.W2);
		t.common[491] = subtract(t.common[490], t.common[431]);
		t.common[492] = subtract(t.common[491], t.common[437]);
		t.common[493] = add(t.common[492], t.common[60]);
		t.common[494] = subtract(t.common[493], t.common[440]);
		t.common[495] = divide(t.b1, number("2"));
		t.common[496] = multiply(t.common[18], t.y2);
		t.common[497] = multiply(t.common[496], t.cotB);
		t.common[498] = multiply(t.common[66], t.cotB);
		t.common[499] = divide(t.common[498], t.W7);
		t.common[500] = multiply(t.common[499], t.W2);
		t.common[501] = multiply(t.common[312], t.cosB);
		t.common[502] = multiply(t.common[501], t.cotB);
		t.common[503] = negate(t.y1);
		t.common[504] = divide(t.common[503], t.common[7]);
		t.common[505] = multiply(t.common[504], t.W5);
		t.common[506] = multiply(t.common[61], t.y1);
		t.common[507] = divide(t.common[506], t.common[63]);
		t.common[508] = multiply(t.common[507], t.y2);
		t.common[509] = divide(t.b2, number("2"));
		t.common[510] = multiply(t.common[184], t.y2);
		t.common[511] = divide(t.common[510], t.W7);
		t.common[512] = divide(t.b3, number("2"));
		t.common[513] = multiply(t.common[21], t.common[196]);
		t.common[514] = divide(t.common[246], t.common[7]);
		t.common[515] = multiply(t.common[247], t.a);
		t.common[516] = divide(t.common[515], t.common[17]);
		t.common[517] = multiply(t.common[65], t.W2);
		t.common[518] = divide(t.common[374], t.common[32]);
		t.common[519] = multiply(t.common[518], t.W2);
	}
	private static void init520(Terms t) {
		t.common[520] = divide(t.common[519], t.rb);
		t.common[521] = divide(t.common[374], t.W7);
		t.common[522] = multiply(t.common[521], t.a);
		t.common[523] = divide(t.common[522], t.common[17]);
		t.common[524] = multiply(t.common[325], t.common[208]);
		t.common[525] = multiply(t.W8, t.cosB);
		t.common[526] = divide(t.common[525], t.rb);
		t.common[527] = divide(t.common[526], t.W7);
		t.common[528] = multiply(t.common[334], t.cosB);
		t.common[529] = divide(t.common[528], t.common[17]);
		t.common[530] = divide(t.common[529], t.W7);
		t.common[531] = divide(t.common[528], t.rb2);
		t.common[532] = divide(t.common[531], t.common[32]);
		t.common[533] = multiply(t.common[352], t.W2);
		t.common[534] = divide(t.common[230], t.rb);
		t.common[535] = multiply(t.common[534], t.y2);
		t.common[536] = subtract(t.common[533], t.common[535]);
		t.common[537] = multiply(t.common[191], t.y2);
		t.common[538] = subtract(t.common[536], t.common[537]);
		t.common[539] = multiply(t.common[235], t.y2);
		t.common[540] = subtract(t.common[538], t.common[539]);
		t.common[541] = multiply(t.common[514], t.W4);
		t.common[542] = divide(t.common[89], t.common[32]);
		t.common[543] = multiply(t.common[542], t.common[289]);
		t.common[544] = multiply(t.common[314], t.cosB);
		t.common[545] = multiply(t.common[8], t.common[311]);
		t.common[546] = multiply(t.common[6], t.cotB);
		t.common[547] = divide(t.common[546], t.common[32]);
		t.common[548] = multiply(t.common[547], t.W9);
		t.common[549] = divide(t.common[260], t.W6);
		t.common[550] = multiply(t.common[2], t.common[23]);
		t.common[551] = divide(t.common[550], t.common[7]);
		t.common[552] = divide(t.common[551], t.rb);
		t.common[553] = add(t.common[549], t.common[552]);
		t.common[554] = divide(t.common[349], t.common[32]);
		t.common[555] = multiply(t.common[554], t.common[346]);
		t.common[556] = divide(t.cosB, t.rb);
		t.common[557] = multiply(t.common[542], t.common[378]);
		t.common[558] = multiply(t.common[317], t.common[396]);
		t.common[559] = multiply(t.common[397], t.y1);
	}
	private static void init560(Terms t) {
		t.common[560] = divide(t.common[559], t.W6);
		t.common[561] = multiply(t.common[560], t.a);
		t.common[562] = divide(t.common[561], t.common[17]);
		t.common[563] = multiply(t.common[562], number("2"));
		t.common[564] = multiply(t.common[563], t.y3b);
		t.common[565] = multiply(t.common[397], t.N1);
		t.common[566] = multiply(t.common[565], t.y2);
		t.common[567] = divide(number("3.0"), number("2.0"));
		t.common[568] = multiply(t.common[567], t.a);
		t.common[569] = multiply(t.common[568], t.y2);
		t.common[570] = multiply(t.common[569], t.W8);
		t.common[571] = multiply(t.common[570], t.cotB);
		t.common[572] = divide(t.common[571], t.common[42]);
		t.common[573] = multiply(t.common[572], number("2"));
		t.common[574] = multiply(t.common[573], t.y3b);
		t.common[575] = divide(t.common[411], t.W6);
		t.common[576] = divide(t.common[415], t.W6);
		t.common[577] = divide(t.common[57], t.common[7]);
		t.common[578] = multiply(t.common[474], t.y1);
		t.common[579] = multiply(t.common[578], t.cotB);
		t.common[580] = divide(t.common[579], t.common[17]);
		t.common[581] = multiply(t.common[580], number("2"));
		t.common[582] = multiply(t.common[581], t.y3b);
		t.common[583] = multiply(t.common[397], t.common[23]);
		t.common[584] = multiply(t.common[568], t.y1);
		t.common[585] = multiply(t.common[584], t.W8);
		t.common[586] = multiply(t.common[585], t.cotB);
		t.common[587] = divide(t.common[586], t.common[42]);
		t.common[588] = multiply(t.common[587], number("2"));
		t.common[589] = multiply(t.common[588], t.y3b);
		t.common[590] = negate(number("1.0"));
		t.common[591] = divide(t.common[590], number("2.0"));
		t.common[592] = divide(t.common[591], t.common[17]);
		t.common[593] = multiply(t.common[188], t.W3);
		t.common[594] = divide(t.common[435], t.cosB);
		t.common[595] = multiply(t.common[594], number("2"));
		t.common[596] = multiply(t.common[595], t.y3b);
		t.common[597] = divide(t.common[314], t.common[63]);
		t.common[598] = multiply(t.common[597], number("2"));
		t.common[599] = multiply(t.common[598], t.y3b);
	}
	private static void init600(Terms t) {
		t.common[600] = multiply(t.common[214], t.common[396]);
		t.common[601] = subtract(t.common[599], t.common[600]);
		t.common[602] = divide(t.b1, number("2.0"));
		t.common[603] = divide(t.common[416], t.rb);
		t.common[604] = divide(t.common[61], t.common[63]);
		t.common[605] = multiply(t.common[71], t.common[23]);
		t.common[606] = divide(t.common[449], t.W7);
		t.common[607] = multiply(t.common[606], t.a);
		t.common[608] = divide(t.common[607], t.common[17]);
		t.common[609] = multiply(t.common[302], t.cosB);
		t.common[610] = divide(t.common[459], t.common[17]);
		t.common[611] = multiply(t.common[610], t.common[461]);
		t.common[612] = multiply(t.common[18], t.common[470]);
		t.common[613] = multiply(number("2"), t.y3b);
		t.common[614] = multiply(t.common[613], t.z1b);
		t.common[615] = divide(t.common[614], t.common[63]);
		t.common[616] = divide(t.z1b, t.rb2);
		t.common[617] = multiply(t.common[616], t.cosB);
		t.common[618] = divide(t.common[467], t.common[17]);
		t.common[619] = divide(t.common[618], t.W7);
		t.common[620] = multiply(t.common[397], t.common[246]);
		t.common[621] = divide(t.common[397], t.common[17]);
		t.common[622] = multiply(t.common[314], t.sinB);
		t.common[623] = multiply(t.common[474], t.z1b);
		t.common[624] = multiply(t.common[71], t.common[246]);
		t.common[625] = multiply(t.common[624], t.sinB);
		t.common[626] = multiply(t.common[334], t.sinB);
	}
	private static DoubleDouble componentXX(Terms t) {
		return add(add(partXX0(t), partXX1(t)), partXX2(t));
	}
	private static DoubleDouble partXX0(Terms t) {
		return multiply(t.b1, divide(divide(multiply(t.common[0],
				add(subtract(subtract(add(subtract(subtract(subtract(subtract(subtract(add(subtract(multiply(multiply(t.common[4],
				t.rFib_ry1), t.common[5]), multiply(divide(t.common[14], t.rb), t.y1)), multiply(t.common[15],
				add(add(subtract(t.common[20], t.common[22]), t.common[26]), t.common[29]))), multiply(t.common[34],
				t.common[36])), multiply(divide(multiply(divide(t.common[31], t.W7), t.a), t.common[17]), t.y1)),
				t.common[44]), multiply(multiply(t.common[47], t.common[54]), t.y1)), multiply(multiply(t.common[56],
				t.common[54]), t.y1)), multiply(t.common[58], subtract(add(subtract(subtract(t.common[59],
				divide(multiply(t.common[24], t.W5), t.rb)), t.common[29]), t.common[60]), t.common[64]))),
				multiply(multiply(divide(t.common[46], t.W7), t.common[82]), t.y1)), multiply(t.common[84],
				t.common[36])), multiply(t.common[85], add(add(multiply(t.common[88], t.common[36]),
				multiply(t.common[65], add(add(multiply(multiply(t.common[91], t.common[68]), t.cotB),
				multiply(multiply(divide(t.common[92], t.common[41]), t.y1), t.cotB)), multiply(multiply(t.common[71],
				t.common[95]), t.cosB)))), multiply(t.common[99], t.y1))))), M_PI), t.common[100]));
	}
	private static DoubleDouble partXX1(Terms t) {
		return multiply(t.b2, divide(divide(multiply(t.common[0],
				add(subtract(add(subtract(add(subtract(subtract(add(add(subtract(multiply(t.N1,
				subtract(divide(multiply(t.common[103], t.y1), t.W6), divide(multiply(t.common[105], t.common[36]),
				t.W7))), multiply(t.common[118], t.y1)), multiply(t.common[119],
				subtract(subtract(add(subtract(add(t.common[49], divide(multiply(t.a, t.cotB), t.rb)), t.common[122]),
				multiply(divide(t.common[123], t.W6), t.W4)), divide(multiply(divide(t.common[124], t.common[7]),
				t.W4), t.rb)), divide(multiply(divide(t.common[124], t.W6), t.a), t.common[17])))),
				multiply(t.common[132], t.common[36])), multiply(t.common[133], add(subtract(t.common[134],
				divide(divide(multiply(t.a, t.common[95]), t.rb), t.cosB)), multiply(t.common[136], t.y1)))),
				t.common[139]), t.common[143]), multiply(t.common[157], t.y1)), multiply(t.common[158],
				add(subtract(add(add(add(subtract(add(multiply(t.common[161], t.y1), t.common[163]),
				multiply(divide(divide(t.common[123], t.rb), t.W6), t.W5)), multiply(divide(divide(t.common[124],
				t.common[17]), t.W6), t.W5)), multiply(divide(divide(t.common[124], t.rb2), t.common[7]), t.W5)),
				multiply(divide(divide(t.common[124], t.common[63]), t.W6), t.a)), multiply(t.common[164], t.y1)),
				divide(multiply(t.common[37], t.common[124]), t.common[42])))), multiply(t.common[179], t.common[36])),
				multiply(t.common[180], add(subtract(add(subtract(divide(divide(t.common[78], t.common[17]), t.cosB),
				divide(divide(multiply(t.common[140], t.y3b), t.common[42]), t.cosB)), multiply(divide(t.common[95],
				t.rb), t.common[176])), multiply(t.common[182], t.y1)), multiply(t.common[172],
				add(add(multiply(t.common[186], t.W9), t.common[189]), t.common[193])))))), M_PI), t.common[100]));
	}
	private static DoubleDouble partXX2(Terms t) {
		return multiply(t.b3, divide(divide(multiply(t.common[0], add(subtract(subtract(subtract(add(multiply(t.N1,
				add(add(subtract(multiply(divide(t.common[197], t.rb), t.y1), t.common[201]), t.common[205]),
				multiply(divide(t.common[207], t.common[17]), t.y1))), t.common[210]), multiply(t.common[57],
				t.common[217])), multiply(multiply(t.common[220], t.common[223]), t.y1)), multiply(t.common[226],
				t.common[36])), multiply(t.common[227], t.common[237]))), M_PI), t.common[100]));
	}
	private static DoubleDouble componentYY(Terms t) {
		return add(add(partYY0(t), partYY1(t)), partYY2(t));
	}
	private static DoubleDouble partYY0(Terms t) {
		return multiply(t.b1, divide(divide(multiply(t.common[0],
				add(subtract(add(subtract(add(add(add(subtract(add(multiply(t.N1,
				subtract(divide(multiply(t.common[239], t.y2), t.W6), divide(multiply(divide(t.common[241], t.rb),
				t.y2), t.W7))), multiply(t.common[251], t.y2)), multiply(t.common[119],
				subtract(subtract(add(multiply(divide(t.common[112], t.common[17]), t.y2),
				multiply(divide(t.common[252], t.W6), t.W4)), divide(multiply(divide(t.common[253], t.common[7]),
				t.W4), t.rb)), divide(multiply(divide(t.common[253], t.W6), t.a), t.common[17])))),
				multiply(divide(t.common[257], t.rb), t.y2)), multiply(divide(t.common[259], t.common[17]), t.y2)),
				t.common[44]), multiply(t.common[272], t.y2)), multiply(t.common[158],
				subtract(add(subtract(subtract(subtract(add(multiply(t.common[273], t.y2),
				multiply(divide(divide(t.common[252], t.rb), t.W6), t.W5)), multiply(divide(divide(t.common[253],
				t.common[17]), t.W6), t.W5)), multiply(divide(divide(t.common[253], t.rb2), t.common[7]), t.W5)),
				multiply(divide(divide(t.common[253], t.common[63]), t.W6), t.a)), multiply(t.common[164], t.y2)),
				divide(multiply(t.common[37], t.common[253]), t.common[42])))), multiply(divide(t.common[292], t.rb),
				t.y2)), multiply(t.common[293], subtract(add(add(subtract(multiply(t.common[295], t.y2),
				multiply(t.common[299], t.y2)), multiply(t.common[301], t.y2)), multiply(multiply(t.common[303],
				t.common[289]), t.y2)), multiply(t.common[283], subtract(add(multiply(t.common[252], t.common[134]),
				multiply(t.common[305], t.y2)), multiply(t.common[307], t.y2))))))), M_PI), t.common[100]));
	}
	private static DoubleDouble partYY1(Terms t) {
		return multiply(t.b2, divide(divide(multiply(t.common[0],
				add(subtract(subtract(add(add(subtract(subtract(add(add(subtract(add(add(subtract(add(subtract(add(multiply(multiply(t.common[308],
				t.rFib_ry2), t.common[5]), multiply(t.common[119], t.common[311])), divide(multiply(t.common[313],
				t.common[311]), t.rb)), multiply(t.common[15], subtract(subtract(multiply(multiply(t.common[315],
				t.y2), t.cotB), t.common[319]), t.common[201]))), multiply(t.common[133], t.W9)),
				divide(multiply(divide(t.common[320], t.common[32]), t.W9), t.rb)),
				divide(divide(multiply(divide(t.common[320], t.W7), t.a), t.common[17]), t.cosB)), t.common[139]),
				t.common[324]), multiply(t.common[326], t.common[333])), multiply(t.common[336], t.common[333])),
				multiply(t.common[338], t.common[333])), multiply(t.common[58], subtract(add(t.common[341],
				multiply(multiply(divide(t.common[52], t.common[17]), t.common[331]), t.y2)), multiply(t.common[330],
				subtract(multiply(t.common[160], t.y2), t.common[342]))))), multiply(divide(divide(t.common[165],
				t.rb), t.W7), t.common[346])), multiply(divide(divide(t.common[347], t.common[17]), t.W7),
				t.common[346])), multiply(divide(divide(t.common[347], t.rb2), t.common[32]), t.common[346])),
				multiply(t.common[350], subtract(subtract(subtract(multiply(t.common[352], t.W9), t.common[354]),
				t.common[355]), multiply(t.common[356], t.y2))))), M_PI), t.common[100]));
	}
	private static DoubleDouble partYY2(Terms t) {
		return multiply(t.b3, divide(divide(multiply(t.common[0], subtract(add(add(subtract(multiply(t.N1,
				subtract(subtract(add(add(divide(multiply(divide(t.common[357], t.rb), t.y2), t.W7),
				multiply(divide(multiply(t.common[358], t.common[196]), t.rb), t.y1)), t.common[201]),
				multiply(divide(t.common[360], t.rb), t.y2)), multiply(t.common[363], t.y2))), t.common[210]),
				multiply(t.common[365], t.common[367])), multiply(divide(t.common[381], t.rb), t.y2)),
				multiply(t.common[293], subtract(add(add(subtract(subtract(multiply(t.common[383], t.y2),
				multiply(t.common[385], t.y2)), multiply(t.common[389], t.y2)), multiply(t.common[390], t.y2)),
				multiply(multiply(t.common[303], t.common[378]), t.y2)), multiply(t.common[283],
				subtract(add(multiply(multiply(t.common[252], t.cosB), t.sinB), multiply(t.common[392], t.y2)),
				multiply(t.common[394], t.y2))))))), M_PI), t.common[100]));
	}
	private static DoubleDouble componentZZ(Terms t) {
		return add(add(partZZ0(t), partZZ1(t)), partZZ2(t));
	}
	private static DoubleDouble partZZ0(Terms t) {
		return multiply(t.b1, divide(divide(multiply(t.common[0],
				add(subtract(subtract(add(add(subtract(add(multiply(t.common[71],
				add(add(subtract(subtract(multiply(multiply(t.N1, t.rFib_ry3), t.cotB),
				multiply(multiply(t.common[358], t.W5), t.common[396])), t.common[403]), t.common[404]),
				t.common[410])), multiply(t.common[411], t.common[413])), multiply(multiply(multiply(t.common[415],
				t.common[413]), number("2")), t.y3b)), multiply(t.common[57], subtract(multiply(t.common[416],
				t.common[396]), multiply(multiply(divide(t.a, t.common[63]), number("2")), t.y3b)))),
				multiply(t.common[418], t.common[421])), multiply(multiply(multiply(t.common[424], t.common[421]),
				number("2")), t.y3b)), multiply(t.common[425], t.W3)), multiply(t.common[227],
				add(subtract(add(add(multiply(t.common[430], t.W2), t.common[431]), t.common[437]), t.common[60]),
				t.common[440])))), M_PI), t.common[100]));
	}
	private static DoubleDouble partZZ1(Terms t) {
		return multiply(t.b2, divide(divide(multiply(t.common[0],
				subtract(add(subtract(add(subtract(add(subtract(subtract(add(add(add(multiply(t.common[441],
				subtract(divide(t.common[396], t.W6), divide(multiply(t.cosB, t.W3), t.W7))), multiply(t.common[444],
				t.common[396])), multiply(multiply(divide(multiply(divide(multiply(t.common[445], t.y1), t.W6), t.a),
				t.common[17]), number("2")), t.y3b)), t.common[448]), multiply(t.common[451], t.W3)),
				multiply(multiply(divide(multiply(divide(multiply(t.common[445], t.z1b), t.W7), t.a), t.common[17]),
				number("2")), t.y3b)), multiply(t.common[89], t.common[452])),
				multiply(multiply(multiply(divide(multiply(t.common[397], t.W8), t.common[17]), t.common[452]),
				number("2")), t.y3b)), multiply(t.common[325], add(t.common[453], t.common[456]))),
				multiply(t.common[457], t.common[472])), multiply(t.common[473], t.W3)), multiply(t.common[293],
				add(subtract(add(subtract(multiply(divide(multiply(t.common[428], t.cotB), t.rb), t.common[461]),
				multiply(multiply(multiply(divide(multiply(t.common[432], t.cotB), t.common[17]), t.common[461]),
				number("2")), t.y3b)), multiply(t.common[460], add(t.common[430], multiply(t.common[187], t.W3)))),
				multiply(multiply(multiply(t.common[475], t.common[470]), number("2")), t.y3b)), multiply(t.common[67],
				add(add(subtract(subtract(add(subtract(divide(negate(t.z1b), t.rb2), divide(multiply(t.y3b, t.sinB),
				t.rb2)), multiply(multiply(divide(t.common[464], t.common[63]), number("2")), t.y3b)),
				divide(divide(multiply(t.sinB, t.W1), t.rb), t.W7)), divide(divide(multiply(t.z1b, t.common[428]),
				t.rb), t.W7)), multiply(multiply(divide(divide(multiply(t.common[476], t.W1), t.common[17]), t.W7),
				number("2")), t.y3b)), multiply(t.common[477], t.W3))))))), M_PI), t.common[100]));
	}
	private static DoubleDouble partZZ2(Terms t) {
		return multiply(t.b3, divide(divide(multiply(t.common[0],
				add(subtract(subtract(add(subtract(subtract(multiply(t.common[71], t.rFib_ry3), multiply(t.common[481],
				t.W3)), multiply(multiply(divide(multiply(divide(multiply(multiply(t.common[445], t.y2), t.sinB),
				t.W7), t.a), t.common[17]), number("2")), t.y3b)), multiply(divide(divide(multiply(t.y2, t.sinB),
				t.rb), t.W7), t.common[483])), multiply(multiply(multiply(divide(divide(multiply(t.common[414],
				t.sinB), t.common[17]), t.W7), t.common[483]), number("2")), t.y3b)), multiply(t.common[487], t.W3)),
				multiply(t.common[488], t.common[494]))), M_PI), t.common[100]));
	}
	private static DoubleDouble componentXY(Terms t) {
		return add(add(add(add(add(partXY0(t), partXY1(t)), partXY2(t)), partXY3(t)), partXY4(t)), partXY5(t));
	}
	private static DoubleDouble partXY0(Terms t) {
		return multiply(t.common[495], divide(divide(multiply(t.common[0],
				add(subtract(subtract(add(add(subtract(subtract(add(subtract(add(subtract(subtract(add(add(subtract(add(multiply(multiply(t.common[4],
				t.rFib_ry2), t.common[5]), multiply(t.common[119], t.common[13])), divide(multiply(t.common[313],
				t.common[13]), t.rb)), multiply(t.common[15], add(add(t.common[497], t.common[319]), t.common[201]))),
				t.common[500]), divide(multiply(divide(t.common[502], t.common[32]), t.W2), t.rb)),
				divide(multiply(divide(t.common[502], t.W7), t.a), t.common[17])), t.common[139]), t.common[324]),
				multiply(t.common[326], t.common[54])), multiply(t.common[336], t.common[54])), multiply(t.common[338],
				t.common[54])), multiply(t.common[58], subtract(subtract(multiply(divide(t.common[505], t.rb), t.y2),
				t.common[201]), t.common[508]))), multiply(divide(t.common[325], t.W7), t.common[82])),
				multiply(divide(t.common[335], t.W7), t.common[82])), multiply(divide(t.common[337], t.common[32]),
				t.common[82])), multiply(t.common[85], add(add(multiply(divide(t.common[88], t.rb), t.y2),
				multiply(t.common[65], add(add(multiply(multiply(t.common[351], t.common[68]), t.cotB),
				multiply(multiply(divide(t.common[92], t.common[17]), t.y2), t.cotB)),
				multiply(multiply(multiply(divide(t.common[71], t.rb), t.sinB), t.y2), t.cosB)))),
				multiply(t.common[99], t.y2))))), M_PI), t.common[100]));
	}
	private static DoubleDouble partXY1(Terms t) {
		return multiply(t.common[509], divide(divide(multiply(t.common[0],
				add(subtract(add(subtract(add(subtract(add(add(subtract(multiply(t.N1,
				subtract(divide(multiply(t.common[103], t.y2), t.W6), divide(multiply(divide(t.common[105], t.rb),
				t.y2), t.W7))), multiply(t.common[118], t.y2)), multiply(t.common[119],
				subtract(subtract(multiply(divide(multiply(multiply(t.common[314], t.y1), t.cotB), t.common[17]),
				t.y2), multiply(t.common[26], t.y2)), multiply(t.common[29], t.y2)))), multiply(divide(t.common[132],
				t.rb), t.y2)), multiply(t.common[133], add(divide(multiply(multiply(divide(t.common[314], t.rb2),
				t.sinB), t.y2), t.cosB), multiply(t.common[136], t.y2)))), t.common[44]), multiply(t.common[157],
				t.y2)), multiply(t.common[158], add(add(add(add(multiply(t.common[161], t.y2),
				multiply(multiply(divide(divide(t.common[23], t.common[41]), t.W6), t.W5), t.y2)),
				multiply(multiply(divide(divide(t.common[23], t.rb2), t.common[7]), t.W5), t.y2)),
				multiply(multiply(divide(divide(t.common[23], t.common[63]), t.W6), t.a), t.y2)),
				multiply(divide(t.common[140], t.common[42]), t.y2)))), multiply(divide(t.common[179], t.rb), t.y2)),
				multiply(t.common[180],
				add(subtract(add(multiply(divide(divide(multiply(multiply(multiply(negate(number("3")), t.a), t.y1),
				t.y3b), t.common[42]), t.cosB), t.y2), multiply(multiply(multiply(t.common[302], t.sinB), t.y2),
				t.common[176])), multiply(t.common[182], t.y2)), multiply(t.common[172],
				add(add(multiply(t.common[511], t.W9), t.common[354]), t.common[355])))))), M_PI), t.common[100]));
	}
	private static DoubleDouble partXY2(Terms t) {
		return multiply(t.common[512], divide(divide(multiply(t.common[0],
				add(subtract(subtract(add(subtract(add(subtract(multiply(t.N1,
				add(add(subtract(subtract(subtract(t.common[513], divide(multiply(t.common[514], t.common[196]),
				t.rb)), t.common[516]), t.common[517]), t.common[520]), t.common[523])), t.common[524]),
				multiply(t.common[335], t.common[208])), multiply(t.common[57], t.common[367])),
				multiply(t.common[527], t.common[223])), multiply(t.common[530], t.common[223])),
				multiply(t.common[532], t.common[223])), multiply(t.common[227], t.common[540]))), M_PI),
				t.common[100]));
	}
	private static DoubleDouble partXY3(Terms t) {
		return multiply(t.common[495], divide(divide(multiply(t.common[0],
				add(subtract(add(subtract(add(subtract(add(add(subtract(subtract(add(multiply(t.N1,
				subtract(divide(multiply(t.common[239], t.y1), t.W6), divide(multiply(t.common[241], t.common[36]),
				t.W7))), multiply(t.common[251], t.y1)), multiply(t.common[119], subtract(subtract(add(t.common[10],
				t.common[122]), multiply(divide(t.common[541], t.rb), t.y1)), multiply(t.common[516], t.y1)))),
				t.common[500]), multiply(t.common[257], t.common[36])), multiply(divide(t.common[259], t.common[41]),
				t.y1)), t.common[139]), t.common[143]), multiply(t.common[272], t.y1)), multiply(t.common[158],
				subtract(subtract(subtract(subtract(add(multiply(t.common[273], t.y1), t.common[163]),
				multiply(multiply(divide(divide(t.common[246], t.common[17]), t.W6), t.W5), t.y1)),
				multiply(multiply(divide(divide(t.common[246], t.rb2), t.common[7]), t.W5), t.y1)),
				multiply(multiply(divide(divide(t.common[246], t.common[63]), t.W6), t.a), t.y1)),
				multiply(divide(t.common[321], t.common[42]), t.y1)))), multiply(t.common[292], t.common[36])),
				multiply(t.common[293], subtract(add(add(subtract(add(subtract(multiply(t.common[295], t.y1),
				multiply(multiply(t.common[162], t.cosB), t.cotB)), divide(t.common[80], t.common[17])),
				multiply(t.common[299], t.y1)), multiply(t.common[301], t.y1)), multiply(t.common[543], t.common[36])),
				multiply(t.common[283], subtract(add(multiply(divide(multiply(t.common[544], t.cotB), t.rb), t.W1),
				multiply(t.common[305], t.y1)), multiply(t.common[307], t.y1))))))), M_PI), t.common[100]));
	}
	private static DoubleDouble partXY4(Terms t) {
		return multiply(t.common[509], divide(divide(multiply(t.common[0],
				add(subtract(subtract(add(subtract(subtract(add(add(add(add(subtract(multiply(multiply(t.common[308],
				t.rFib_ry1), t.common[5]), multiply(divide(t.common[545], t.rb), t.y1)), multiply(t.common[15],
				subtract(subtract(add(multiply(multiply(t.common[315], t.y1), t.cotB), t.common[22]), t.common[26]),
				t.common[29]))), multiply(t.common[548], t.common[36])),
				multiply(divide(divide(multiply(divide(t.common[546], t.W7), t.a), t.common[17]), t.cosB), t.y1)),
				t.common[44]), multiply(multiply(t.common[47], t.common[333]), t.y1)), multiply(multiply(t.common[56],
				t.common[333]), t.y1)), multiply(t.common[58], subtract(add(subtract(t.common[553],
				multiply(t.common[67], t.common[331])), multiply(t.common[154], t.common[331])),
				multiply(t.common[330], subtract(multiply(t.common[160], t.y1), t.common[216]))))),
				multiply(multiply(divide(divide(t.common[348], t.common[17]), t.W7), t.common[346]), t.y1)),
				multiply(t.common[555], t.common[36])), multiply(t.common[350],
				subtract(subtract(subtract(multiply(t.common[228], t.W9), t.common[189]), t.common[193]),
				multiply(t.common[356], t.y1))))), M_PI), t.common[100]));
	}
	private static DoubleDouble partXY5(Terms t) {
		return multiply(t.common[512], divide(divide(multiply(t.common[0], subtract(add(add(subtract(add(multiply(t.N1,
				subtract(subtract(add(add(add(subtract(divide(multiply(t.common[357], t.common[36]), t.W7),
				t.common[513]), divide(multiply(t.common[24], t.common[196]), t.rb)), t.common[29]), t.common[517]),
				multiply(t.common[360], t.common[36])), multiply(t.common[363], t.y1))), t.common[524]),
				multiply(divide(multiply(t.common[23], t.W8), t.common[17]), t.common[208])), multiply(t.common[365],
				t.common[217])), multiply(t.common[381], t.common[36])), multiply(t.common[293],
				subtract(add(add(subtract(subtract(add(multiply(t.common[383], t.y1), multiply(t.common[556],
				t.common[371])), multiply(t.common[385], t.y1)), multiply(t.common[389], t.y1)),
				multiply(t.common[390], t.y1)), multiply(t.common[557], t.common[36])), multiply(t.common[283],
				subtract(add(multiply(divide(t.common[544], t.rb), t.W1), multiply(t.common[392], t.y1)),
				multiply(t.common[394], t.y1))))))), M_PI), t.common[100]));
	}
	private static DoubleDouble componentXZ(Terms t) {
		return add(add(add(add(add(partXZ0(t), partXZ1(t)), partXZ2(t)), partXZ3(t)), partXZ4(t)), partXZ5(t));
	}
	private static DoubleDouble partXZ0(Terms t) {
		return multiply(t.common[495], divide(divide(multiply(t.common[0],
				add(subtract(subtract(add(add(subtract(subtract(add(subtract(add(subtract(subtract(add(subtract(multiply(multiply(t.common[4],
				t.rFib_ry3), t.common[5]), multiply(t.common[14], t.common[396])), multiply(t.common[15],
				add(add(multiply(multiply(multiply(t.common[475], number("2")), t.y3b), t.cotB), t.common[558]),
				t.common[564]))), multiply(t.common[34], t.W3)),
				multiply(multiply(divide(multiply(divide(multiply(multiply(t.common[566], t.cosB), t.cotB), t.W7),
				t.a), t.common[17]), number("2")), t.y3b)), t.common[497]), t.common[574]), multiply(t.common[575],
				t.common[54])), multiply(multiply(multiply(t.common[576], t.common[54]), number("2")), t.y3b)),
				multiply(multiply(t.common[577], t.common[54]), t.common[396])), multiply(t.common[58],
				subtract(subtract(multiply(t.common[505], t.common[396]), t.common[564]), t.common[456]))),
				multiply(divide(t.common[411], t.W7), t.common[82])), multiply(multiply(multiply(divide(t.common[415],
				t.W7), t.common[82]), number("2")), t.y3b)), multiply(t.common[84], t.W3)), multiply(t.common[85],
				add(subtract(add(multiply(t.common[88], t.W3), multiply(t.common[65],
				add(add(multiply(multiply(t.common[428], t.common[68]), t.cotB),
				multiply(multiply(multiply(divide(multiply(t.common[432], t.a), t.common[17]), number("2")), t.y3b),
				t.cotB)), multiply(multiply(multiply(multiply(divide(t.common[445], t.rb), t.sinB), number("2")),
				t.y3b), t.cosB)))), divide(multiply(t.common[275], t.cotB), t.rb2)),
				multiply(multiply(divide(t.common[80], t.common[63]), number("2")), t.y3b))))), M_PI), t.common[100]));
	}
	private static DoubleDouble partXZ1(Terms t) {
		return multiply(t.common[509], divide(divide(multiply(t.common[0],
				add(subtract(add(add(subtract(add(add(subtract(subtract(add(add(subtract(multiply(t.N1,
				subtract(divide(multiply(t.common[102], t.common[396]), t.W6), divide(multiply(t.common[105], t.W3),
				t.W7))), multiply(t.common[117], t.common[396])), multiply(t.common[119],
				subtract(subtract(subtract(t.nu, t.common[582]), multiply(t.common[25], t.common[396])),
				multiply(multiply(divide(multiply(divide(t.common[583], t.W6), t.a), t.common[17]), number("2")),
				t.y3b)))), multiply(t.common[132], t.W3)), multiply(t.common[133], add(subtract(t.common[458],
				divide(multiply(multiply(multiply(divide(t.common[474], t.rb2), t.sinB), number("2")), t.y3b),
				t.cosB)), multiply(multiply(divide(divide(multiply(t.common[474], t.common[73]), t.common[17]),
				t.cosB), number("2")), t.y3b)))), t.common[20]), t.common[589]), multiply(t.common[21],
				t.common[155])), multiply(t.common[156], t.common[396])), multiply(t.common[158],
				add(add(add(add(multiply(multiply(multiply(t.common[592], t.common[147]), number("2")), t.y3b),
				multiply(multiply(multiply(divide(divide(t.common[583], t.common[17]), t.W6), t.W5), number("2")),
				t.y3b)), multiply(multiply(divide(t.common[150], t.common[7]), t.W5), t.common[396])),
				multiply(multiply(multiply(divide(divide(t.common[583], t.common[63]), t.W6), t.a), number("2")),
				t.y3b)), multiply(multiply(divide(multiply(t.common[568], t.common[23]), t.common[42]), number("2")),
				t.y3b)))), multiply(divide(t.cotB, t.W7), t.common[178])), multiply(multiply(t.common[166],
				add(add(t.common[167], divide(divide(t.common[168], t.common[41]), t.cosB)), t.common[177])), t.W3)),
				multiply(t.common[180], add(subtract(add(subtract(multiply(divide(t.common[18], t.cosB), t.y1),
				multiply(multiply(divide(divide(multiply(t.common[584], t.y3b), t.common[42]), t.cosB), number("2")),
				t.y3b)), multiply(multiply(multiply(multiply(divide(t.common[397], t.rb2), t.sinB), number("2")),
				t.y3b), t.common[176])), multiply(multiply(multiply(divide(multiply(t.common[397], t.common[73]),
				t.common[41]), t.common[176]), number("2")), t.y3b)), multiply(t.common[172],
				add(add(multiply(t.common[430], t.W9), t.common[593]), t.common[596])))))), M_PI), t.common[100]));
	}
	private static DoubleDouble partXZ2(Terms t) {
		return multiply(t.common[512], divide(divide(multiply(t.common[0],
				add(subtract(subtract(add(subtract(add(subtract(multiply(t.N1, add(add(subtract(multiply(t.common[197],
				t.common[396]), t.common[403]), t.common[404]), t.common[410])), multiply(t.common[411],
				t.common[208])), multiply(multiply(multiply(t.common[415], t.common[208]), number("2")), t.y3b)),
				multiply(t.common[57], t.common[601])), multiply(t.common[418], t.common[223])),
				multiply(multiply(multiply(t.common[424], t.common[223]), number("2")), t.y3b)),
				multiply(t.common[226], t.W3)), multiply(t.common[227], t.common[494]))), M_PI), t.common[100]));
	}
	private static DoubleDouble partXZ3(Terms t) {
		return multiply(t.common[602], divide(divide(multiply(t.common[0],
				add(subtract(subtract(add(subtract(multiply(t.common[71],
				add(add(subtract(subtract(multiply(multiply(t.N1, t.rFib_ry1), t.cotB),
				multiply(divide(multiply(t.common[316], t.W5), t.rb), t.y2)), t.common[201]), t.common[205]),
				multiply(divide(t.common[207], t.common[41]), t.y1))), multiply(multiply(t.common[46], t.common[413]),
				t.y1)), multiply(t.common[57], subtract(multiply(t.common[603], t.y1), multiply(t.common[604],
				t.y1)))), multiply(multiply(t.common[220], t.common[421]), t.y1)), multiply(t.common[425],
				t.common[36])), multiply(t.common[227], add(add(add(multiply(t.common[186], t.W2), t.common[231]),
				t.common[233]), t.common[236])))), M_PI), t.common[100]));
	}
	private static DoubleDouble partXZ4(Terms t) {
		return multiply(t.common[509], divide(divide(multiply(t.common[0],
				subtract(add(add(subtract(subtract(subtract(add(add(add(subtract(multiply(t.common[441],
				subtract(divide(multiply(t.common[89], t.y1), t.W6), divide(multiply(t.cosB, t.common[36]), t.W7))),
				multiply(divide(t.common[71], t.W6), t.W5)), divide(multiply(divide(t.common[605], t.common[7]), t.W5),
				t.rb)), divide(multiply(divide(t.common[605], t.W6), t.a), t.common[17])),
				multiply(divide(t.common[173], t.W7), t.W2)), multiply(t.common[451], t.common[36])),
				multiply(t.common[608], t.y1)), multiply(multiply(divide(t.W8, t.common[17]), t.common[452]), t.y1)),
				multiply(t.common[325], add(subtract(t.common[553], t.common[60]), t.common[64]))),
				multiply(t.common[473], t.common[36])), multiply(t.common[293],
				add(subtract(add(subtract(multiply(multiply(multiply(t.common[609], t.y1), t.cotB), t.common[461]),
				multiply(t.common[611], t.y1)), multiply(t.common[460], add(t.common[186], multiply(t.common[187],
				t.common[36])))), multiply(t.common[612], t.y1)), multiply(t.common[67],
				add(add(subtract(subtract(add(divide(multiply(negate(t.y3b), t.cosB), t.rb2), multiply(t.common[615],
				t.y1)), divide(divide(multiply(t.cosB, t.W1), t.rb), t.W7)), divide(multiply(t.common[617], t.y1),
				t.W7)), multiply(t.common[619], t.y1)), multiply(t.common[477], t.common[36]))))))), M_PI),
				t.common[100]));
	}
	private static DoubleDouble partXZ5(Terms t) {
		return multiply(t.common[512], divide(divide(multiply(t.common[0],
				add(subtract(subtract(subtract(subtract(multiply(t.common[71], t.rFib_ry1), multiply(t.common[481],
				t.common[36])), multiply(divide(multiply(divide(t.common[479], t.W7), t.a), t.common[17]), t.y1)),
				multiply(multiply(divide(divide(t.common[484], t.common[17]), t.W7), t.common[483]), t.y1)),
				multiply(t.common[487], t.common[36])), multiply(t.common[488], t.common[237]))), M_PI),
				t.common[100]));
	}
	private static DoubleDouble componentYZ(Terms t) {
		return add(add(add(add(add(partYZ0(t), partYZ1(t)), partYZ2(t)), partYZ3(t)), partYZ4(t)), partYZ5(t));
	}
	private static DoubleDouble partYZ0(Terms t) {
		return multiply(t.common[495], divide(divide(multiply(t.common[0],
				add(subtract(add(add(subtract(add(add(subtract(add(add(subtract(subtract(add(multiply(t.N1,
				subtract(divide(multiply(t.common[238], t.common[396]), t.W6), divide(multiply(t.common[241], t.W3),
				t.W7))), multiply(t.common[250], t.common[396])), multiply(t.common[119],
				subtract(subtract(add(t.common[582], t.nu), multiply(t.common[541], t.common[396])),
				multiply(multiply(divide(multiply(divide(t.common[620], t.W6), t.a), t.common[17]), number("2")),
				t.y3b)))), multiply(divide(multiply(multiply(t.N1, t.sinB), t.cotB), t.W7), t.W2)),
				multiply(t.common[257], t.W3)),
				multiply(multiply(divide(multiply(divide(multiply(multiply(t.common[565], t.z1b), t.cotB), t.W7), t.a),
				t.common[17]), number("2")), t.y3b)), t.common[20]), t.common[589]), multiply(t.common[21],
				t.common[270])), multiply(t.common[271], t.common[396])), multiply(t.common[158],
				subtract(subtract(subtract(subtract(multiply(multiply(multiply(t.common[592], t.common[261]),
				number("2")), t.y3b), multiply(multiply(multiply(divide(divide(t.common[620], t.common[17]), t.W6),
				t.W5), number("2")), t.y3b)), multiply(multiply(divide(t.common[264], t.common[7]), t.W5),
				t.common[396])), multiply(multiply(multiply(divide(divide(t.common[620], t.common[63]), t.W6), t.a),
				number("2")), t.y3b)), multiply(multiply(divide(multiply(t.common[568], t.common[246]), t.common[42]),
				number("2")), t.y3b)))), multiply(t.common[457], subtract(add(t.common[278], divide(t.common[280],
				t.common[41])), t.common[290]))), multiply(t.common[292], t.W3)), multiply(t.common[293],
				subtract(add(add(subtract(add(add(subtract(multiply(multiply(multiply(t.common[621], t.common[276]),
				number("2")), t.y3b), multiply(multiply(t.common[162], t.sinB), t.cotB)), t.common[304]),
				divide(multiply(multiply(t.common[78], t.sinB), t.cotB), t.common[17])),
				multiply(multiply(divide(multiply(multiply(multiply(t.common[568], t.y3b), t.z1b), t.cotB),
				t.common[42]), number("2")), t.y3b)), multiply(multiply(multiply(divide(divide(t.common[397],
				t.common[41]), t.W7), t.common[289]), number("2")), t.y3b)), multiply(t.common[543], t.W3)),
				multiply(t.common[283], subtract(add(multiply(divide(multiply(t.common[622], t.cotB), t.rb), t.W1),
				multiply(multiply(multiply(divide(multiply(t.common[623], t.cotB), t.common[17]), t.W1), number("2")),
				t.y3b)), multiply(t.common[287], t.common[428]))))))), M_PI), t.common[100]));
	}
	private static DoubleDouble partYZ1(Terms t) {
		return multiply(t.common[509], divide(divide(multiply(t.common[0],
				add(subtract(subtract(add(add(subtract(subtract(add(add(subtract(add(add(add(subtract(multiply(multiply(t.common[308],
				t.rFib_ry3), t.common[5]), multiply(t.common[545], t.common[396])), multiply(t.common[15],
				subtract(subtract(multiply(multiply(multiply(divide(multiply(t.common[591], t.a), t.common[17]),
				number("2")), t.y3b), t.cotB), t.common[558]), t.common[564]))), multiply(t.common[548], t.W3)),
				multiply(multiply(divide(divide(multiply(divide(multiply(t.common[566], t.cotB), t.W7), t.a),
				t.common[17]), t.cosB), number("2")), t.y3b)), t.common[497]), t.common[574]), multiply(t.common[575],
				t.common[333])), multiply(multiply(multiply(t.common[576], t.common[333]), number("2")), t.y3b)),
				multiply(multiply(t.common[577], t.common[333]), t.common[396])), multiply(t.common[58],
				subtract(add(t.common[453], multiply(multiply(multiply(divide(t.common[578], t.common[17]),
				t.common[331]), number("2")), t.y3b)), multiply(t.common[330],
				subtract(multiply(multiply(t.common[592], number("2")), t.y3b), t.common[600]))))),
				multiply(divide(divide(multiply(t.y2, t.cotB), t.rb), t.W7), t.common[346])),
				multiply(multiply(multiply(divide(divide(multiply(t.common[414], t.cotB), t.common[17]), t.W7),
				t.common[346]), number("2")), t.y3b)), multiply(t.common[555], t.W3)), multiply(t.common[350],
				subtract(add(subtract(subtract(multiply(t.common[489], t.W9), t.common[593]), t.common[596]),
				divide(t.common[60], t.cosB)), multiply(multiply(divide(t.common[438], t.cosB), number("2")),
				t.y3b))))), M_PI), t.common[100]));
	}
	private static DoubleDouble partYZ2(Terms t) {
		return multiply(t.common[512], divide(divide(multiply(t.common[0],
				subtract(add(subtract(add(subtract(add(multiply(t.N1,
				subtract(subtract(add(add(add(divide(multiply(t.common[357], t.W3), t.W7),
				multiply(multiply(t.common[316], t.common[196]), t.common[396])), t.common[564]),
				multiply(divide(t.sinB, t.W7), t.W2)), multiply(t.common[360], t.W3)),
				multiply(multiply(divide(multiply(divide(t.common[476], t.W7), t.a), t.common[17]), number("2")),
				t.y3b))), multiply(t.common[35], t.common[208])),
				multiply(multiply(multiply(divide(multiply(t.common[559], t.W8), t.common[41]), t.common[208]),
				number("2")), t.y3b)), multiply(t.common[365], t.common[601])), multiply(t.common[457],
				t.common[380])), multiply(t.common[381], t.W3)), multiply(t.common[293],
				subtract(add(add(add(subtract(add(multiply(multiply(divide(multiply(multiply(t.common[397], t.sinB),
				t.a), t.common[17]), number("2")), t.y3b), multiply(divide(t.sinB, t.rb), t.common[371])),
				multiply(multiply(multiply(divide(t.common[476], t.common[17]), t.common[371]), number("2")), t.y3b)),
				multiply(t.common[370], subtract(t.common[60], t.common[440]))),
				multiply(multiply(multiply(divide(t.common[621], t.W7), t.common[378]), number("2")), t.y3b)),
				multiply(t.common[557], t.W3)), multiply(t.common[283], subtract(add(multiply(divide(t.common[622],
				t.rb), t.W1), multiply(multiply(multiply(divide(t.common[623], t.common[17]), t.W1), number("2")),
				t.y3b)), multiply(t.common[376], t.common[428]))))))), M_PI), t.common[100]));
	}
	private static DoubleDouble partYZ3(Terms t) {
		return multiply(t.common[602], divide(divide(multiply(t.common[0],
				add(subtract(subtract(add(add(subtract(add(multiply(t.common[71],
				add(add(subtract(subtract(subtract(add(multiply(multiply(t.N1, t.rFib_ry2), t.cotB), t.common[59]),
				divide(multiply(t.common[514], t.W5), t.rb)), t.common[516]), t.common[517]), t.common[520]),
				t.common[523])), multiply(t.common[325], t.common[413])), multiply(t.common[335], t.common[413])),
				multiply(t.common[57], subtract(multiply(t.common[603], t.y2), multiply(t.common[604], t.y2)))),
				multiply(t.common[527], t.common[421])), multiply(t.common[530], t.common[421])),
				multiply(t.common[532], t.common[421])), multiply(t.common[227], add(add(add(multiply(t.common[511],
				t.W2), t.common[535]), t.common[537]), t.common[539])))), M_PI), t.common[100]));
	}
	private static DoubleDouble partYZ4(Terms t) {
		return multiply(t.common[509], divide(divide(multiply(t.common[0],
				subtract(add(add(subtract(subtract(subtract(add(add(multiply(t.common[441],
				subtract(divide(multiply(t.common[89], t.y2), t.W6), divide(multiply(t.common[556], t.y2), t.W7))),
				multiply(divide(t.common[444], t.rb), t.y2)), multiply(divide(multiply(divide(t.common[442], t.W6),
				t.a), t.common[17]), t.y2)), multiply(divide(t.common[451], t.rb), t.y2)), multiply(t.common[608],
				t.y2)), multiply(multiply(divide(t.W8, t.common[41]), t.common[452]), t.y2)), multiply(t.common[325],
				add(t.common[341], t.common[508]))), multiply(divide(t.common[473], t.rb), t.y2)),
				multiply(t.common[293], add(subtract(add(subtract(multiply(multiply(multiply(t.common[609], t.y2),
				t.cotB), t.common[461]), multiply(t.common[611], t.y2)), multiply(t.common[460],
				add(divide(multiply(divide(t.common[86], t.rb), t.y2), t.W7), multiply(divide(t.common[187], t.rb),
				t.y2)))), multiply(t.common[612], t.y2)), multiply(t.common[67],
				add(add(subtract(multiply(t.common[615], t.y2), divide(multiply(t.common[617], t.y2), t.W7)),
				multiply(t.common[619], t.y2)), multiply(divide(divide(t.common[467], t.rb2), t.common[32]),
				t.y2))))))), M_PI), t.common[100]));
	}
	private static DoubleDouble partYZ5(Terms t) {
		return multiply(t.common[512], divide(divide(multiply(t.common[0],
				add(subtract(subtract(add(subtract(subtract(add(multiply(t.common[71], t.rFib_ry2), t.common[448]),
				divide(multiply(divide(t.common[625], t.common[32]), t.W2), t.rb)),
				divide(multiply(divide(t.common[625], t.W7), t.a), t.common[17])),
				multiply(divide(divide(multiply(t.W8, t.sinB), t.rb), t.W7), t.common[483])),
				multiply(divide(divide(t.common[626], t.common[17]), t.W7), t.common[483])),
				multiply(divide(divide(t.common[626], t.rb2), t.common[32]), t.common[483])), multiply(t.common[488],
				t.common[540]))), M_PI), t.common[100]));
	}
	// @formatter:on

	private static DoubleDouble value(double v) {
		return DoubleDouble.of(v);
	}

	private static DoubleDouble number(String v) {
		switch (v) {
		case "1":
		case "1.0":
			return DoubleDouble.ONE;
		case "2":
		case "2.0":
			return DoubleDouble.TWO;
		case "3":
		case "3.0":
			return DoubleDouble.THREE;
		case "4":
		case "4.0":
			return DoubleDouble.FOUR;
		case "3.14159265358979323846264338327950288419716939937510582097494459":
			return PI_EXTENDED;
		default:
			return DoubleDouble.parse(v);
		}
	}

	private static DoubleDouble add(DoubleDouble a, DoubleDouble b) {
		return a.add(b);
	}

	private static DoubleDouble subtract(DoubleDouble a, DoubleDouble b) {
		return a.subtract(b);
	}

	private static DoubleDouble multiply(DoubleDouble a, DoubleDouble b) {
		return a.multiply(b);
	}

	private static DoubleDouble divide(DoubleDouble a, DoubleDouble b) {
		return a.divide(b);
	}

	private static DoubleDouble negate(DoubleDouble a) {
		return a.negate();
	}

	private static DoubleDouble sqrt(DoubleDouble a) {
		return a.sqrt();
	}

}
