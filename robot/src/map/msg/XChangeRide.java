
package map.msg;

import com.goldhuman.Common.Marshal.OctetsStream;
import com.goldhuman.Common.Marshal.MarshalException;

// {{{ RPCGEN_IMPORT_BEGIN
// {{{ DO NOT EDIT THIS

abstract class __XChangeRide__ extends xio.Protocol { }

// DO NOT EDIT THIS }}}
// RPCGEN_IMPORT_END }}}

public class XChangeRide extends __XChangeRide__ {
	@Override
	protected void process() {
		// protocol handle
	}

	// {{{ RPCGEN_DEFINE_BEGIN
	// {{{ DO NOT EDIT THIS
	public static final int PROTOCOL_TYPE = 6714576;

	public int getType() {
		return 6714576;
	}

	public int rideid;

	public XChangeRide() {
	}

	public XChangeRide(int _rideid_) {
		this.rideid = _rideid_;
	}

	public final boolean _validator_() {
		return true;
	}

	public OctetsStream marshal(OctetsStream _os_) {
		_os_.marshal(rideid);
		return _os_;
	}

	public OctetsStream unmarshal(OctetsStream _os_) throws MarshalException {
		rideid = _os_.unmarshal_int();
		return _os_;
	}

	public boolean equals(Object _o1_) {
		if (_o1_ == this) return true;
		if (_o1_ instanceof XChangeRide) {
			XChangeRide _o_ = (XChangeRide)_o1_;
			if (rideid != _o_.rideid) return false;
			return true;
		}
		return false;
	}

	public int hashCode() {
		int _h_ = 0;
		_h_ += rideid;
		return _h_;
	}

	public String toString() {
		StringBuilder _sb_ = new StringBuilder();
		_sb_.append("(");
		_sb_.append(rideid).append(",");
		_sb_.append(")");
		return _sb_.toString();
	}

	public int compareTo(XChangeRide _o_) {
		if (_o_ == this) return 0;
		int _c_ = 0;
		_c_ = rideid - _o_.rideid;
		if (0 != _c_) return _c_;
		return _c_;
	}

	// DO NOT EDIT THIS }}}
	// RPCGEN_DEFINE_END }}}

}

