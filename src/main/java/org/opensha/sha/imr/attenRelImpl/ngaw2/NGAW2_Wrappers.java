package org.opensha.sha.imr.attenRelImpl.ngaw2;

import org.opensha.commons.param.event.ParameterChangeWarningListener;

@SuppressWarnings("javadoc")
public class NGAW2_Wrappers {
	public static class ASK_2014_Wrapper extends NGAW2_WrapperFullParam {
		
		public ASK_2014_Wrapper() {
			this(null);
		}

		public ASK_2014_Wrapper(ParameterChangeWarningListener l) {
			super(ASK_2014.SHORT_NAME, new ASK_2014(), true);
			this.listener = l;
		}
		
	}
	public static class BSSA_2014_Wrapper extends NGAW2_WrapperFullParam {
		
		public BSSA_2014_Wrapper() {
			this(null);
		}

		public BSSA_2014_Wrapper(ParameterChangeWarningListener l) {
			super(BSSA_2014.SHORT_NAME, new BSSA_2014(), true);
			this.listener = l;
		}
		
	}
	public static class CB_2014_Wrapper extends NGAW2_WrapperFullParam {
		
		public CB_2014_Wrapper() {
			this(null);
		}

		public CB_2014_Wrapper(ParameterChangeWarningListener l) {
			super(CB_2014.SHORT_NAME, new CB_2014(), true);
			this.listener = l;
		}
		
	}
	public static class CY_2014_Wrapper extends NGAW2_WrapperFullParam {
		
		public CY_2014_Wrapper() {
			this(null);
		}

		public CY_2014_Wrapper(ParameterChangeWarningListener l) {
			super(CY_2014.SHORT_NAME, new CY_2014(), true);
			this.listener = l;
		}
		
	}
	public static class GK_2014_Wrapper extends NGAW2_WrapperFullParam {
		
		public GK_2014_Wrapper() {
			this(null);
		}

		public GK_2014_Wrapper(ParameterChangeWarningListener l) {
			super(GK_2014.SHORT_NAME, new GK_2014(), false);
			this.listener = l;
		}
		
	}
	public static class Idriss_2014_Wrapper extends NGAW2_WrapperFullParam {
		
		public Idriss_2014_Wrapper() {
			this(null);
		}

		public Idriss_2014_Wrapper(ParameterChangeWarningListener l) {
			super(Idriss_2014.SHORT_NAME, new Idriss_2014(), false);
			this.listener = l;
		}
		
	}
}
