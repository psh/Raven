"""Python: one module, ctypes, no dependency."""

from cparse import classify, doc_lines

SCALAR = {"int": "ctypes.c_int", "float": "ctypes.c_float", "double": "ctypes.c_double", "char": "ctypes.c_char",
          "size_t": "ctypes.c_size_t", "uint64": "ctypes.c_uint64", "uintptr": "ctypes.c_size_t", "uint32": "ctypes.c_uint32",
          "uint8": "ctypes.c_uint8", "id": "ctypes.c_uint32"}


def ctype_of(api, ctype, in_struct=False):
    kind, detail = classify(ctype, api)
    if kind == "void":
        return "None"
    if kind == "scalar":
        return SCALAR[detail]
    if kind == "enum":
        return "ctypes.c_int"
    if kind == "callback":
        return detail
    if kind in ("fnptr", "ptr", "cptr", "bytes", "cbytes", "opaque_ptr", "owned_str"):
        return "ctypes.c_void_p"
    if kind == "cstr":
        return "ctypes.c_char_p"
    if kind == "cstr_out":
        return "ctypes.POINTER(ctypes.c_char_p)"
    if kind == "cstr_array":
        return "ctypes.POINTER(ctypes.c_char_p)"
    if kind == "out":
        inner = SCALAR[detail[1]] if detail[0] == "scalar" else "ctypes.c_int"
        return "ctypes.POINTER(%s)" % inner
    if kind == "carray":
        return "ctypes.POINTER(%s)" % SCALAR[detail]
    if kind == "struct":
        return detail
    if kind in ("struct_ptr", "cstruct_ptr"):
        return "ctypes.POINTER(%s)" % detail
    raise ValueError(ctype)


HEADER = '''"""
Raven for Python.

Generated from the C headers by tools/bindgen (Raven %(version)s): do not edit.
Every function of the C API is here under its own name, with a few
conveniences:

  - texts are str on both sides (UTF-8 underneath);
  - a Python function can be given wherever the C API takes a callback: it
    is kept alive for as long as the library runs; the texts it receives are
    str, the structures it receives are ctypes structures;
  - a parameter the C function writes through (int*, float*...) is not
    passed: its value is returned, after the result of the function if it
    has one. Rwidget_get_rect(widget) returns (x, y, width, height);
  - the size that follows a text or bytes (xml, size) is not passed either;
  - a structure is passed as it is (ctypes passes its address);
  - `user_data` can be left out.

The library itself (raven.dll, libraven.so, libraven.dylib: build Raven with
-DRAVEN_BUILD_SHARED=ON) is looked for in the RAVEN_LIBRARY environment
variable, then beside this file, then where the system looks.

    import raven as r
    r.Rinit(r.R_BACKEND_DEFAULT)
    window = r.Rwindow_create_from_xml('<Window Title="Hi"><Button Text="Quit" OnClicked="quit"/></Window>')
    r.Raction_bind("quit", lambda sender, action, user: r.Rquit(0))
    while r.Rwait_frame(-1.0):
        pass
    r.Rshutdown()
"""

import ctypes
import enum
import os
import sys


def _load():
    names = {"win32": ["raven.dll", "libraven.dll"], "darwin": ["libraven.dylib"]}.get(sys.platform, ["libraven.so", "libraven.so.0"])
    tried = []
    explicit = os.environ.get("RAVEN_LIBRARY")
    candidates = [explicit] if explicit else []
    here = os.path.dirname(os.path.abspath(__file__))
    candidates += [os.path.join(here, name) for name in names] + names
    for candidate in candidates:
        try:
            return ctypes.CDLL(candidate)
        except OSError as error:
            tried.append("%%s (%%s)" %% (candidate, error))
    raise OSError("the Raven library was not found; set RAVEN_LIBRARY to its path. Tried:\\n  " + "\\n  ".join(tried))


_lib = _load()

# What must outlive a call: the callbacks given to the library, and the texts they return.
_alive = []


def _s(text):
    """A text for the library."""
    if text is None or isinstance(text, bytes):
        return text
    return str(text).encode("utf-8")


def _r(text):
    """A text from the library."""
    if text is None:
        return None
    return text.decode("utf-8", "replace")


def _p(value):
    """A structure for the library: its address. None and arrays pass as they are."""
    if isinstance(value, ctypes.Structure):
        return ctypes.byref(value)
    return value


def _a(ctype, values):
    """Numbers for the library (a list, a tuple, any sequence): copied into a C array. None and C arrays pass as they are."""
    if values is None or isinstance(values, (ctypes.Array, ctypes._Pointer)):
        return values
    return (ctype * len(values))(*values)


def _e(cls, value):
    """A number as a member of an enumeration, when it is one."""
    try:
        return cls(value)
    except ValueError:
        return value


def _callback(ctype, adapt, function):
    """A Python function as a C callback, kept alive."""
    if function is None:
        return ctypes.cast(None, ctype)
    if isinstance(function, ctype):
        return function
    wrapped = ctype(adapt(function))
    _alive.append(wrapped)
    return wrapped


Rid = ctypes.c_uint32
'''


def py_name(name):
    return name + "_" if name in ("from", "class", "def", "global", "in", "is", "lambda", "pass", "type", "id", "filter", "format") else name


def emit_defines(out, api):
    for name, kind, value in api.defines:
        if kind == "str":
            out.append('%s = "%s"' % (name, value))
        else:
            out.append("%s = %s" % (name, hex(value) if kind == "id" else value))


def emit_enums(out, api):
    for e in api.enums:
        out.append("")
        out.append("class %s(enum.Int%s):" % (e.name, "Flag" if e.flags else "Enum"))
        lines = doc_lines(e.doc)
        if lines:
            out.append('    """%s"""' % "\n    ".join(lines).replace('"""', "'''"))
        seen = {}
        for item, value, doc in e.items:
            out.append("    %s = 0x%04X" % (item, value) if value > 9 else "    %s = %d" % (item, value))
            seen[item] = value
        out.append("")
        for item, value, doc in e.items:
            out.append("%s = %s.%s" % (item, e.name, item))


def emit_structs(out, api):
    for s in api.structs:
        out.append("")
        out.append("")
        out.append("class %s(ctypes.Structure):" % s.name)
        lines = doc_lines(s.doc)
        if lines:
            out.append('    """%s"""' % "\n    ".join(lines).replace('"""', "'''"))
        out.append("    _fields_ = [")
        for f in s.fields:
            kind, detail = classify(f.ctype, api)
            t = ctype_of(api, f.ctype, True)
            # (a pointer to structures inside a structure: an array the caller builds)
            if f.array:
                t = "%s * %d" % (t, f.array)
            out.append('        ("%s", %s),' % (f.name, t))
        out.append("    ]")


def emit_callbacks(out, api):
    out.append("")
    for c in api.callbacks:
        ret_kind, ret_detail = classify(c.ret, api)
        # a text returned by Python must stay alive: it travels as an address
        ret = "ctypes.c_void_p" if ret_kind == "cstr" else ctype_of(api, c.ret)
        out.append("")
        out.append("%s = ctypes.CFUNCTYPE(%s)" % (c.name, ", ".join([ret] + [ctype_of(api, p.ctype) for p in c.params])))
        out.append("")
        out.append("")
        out.append("def _adapt_%s(function):" % c.name)
        names = [py_name(p.name) for p in c.params]
        out.append("    def call(%s):" % ", ".join(names))
        args = []
        for p in c.params:
            kind, detail = classify(p.ctype, api)
            n = py_name(p.name)
            if kind == "cstr":
                args.append("_r(%s)" % n)
            elif kind in ("struct_ptr", "cstruct_ptr"):
                args.append("%s.contents if %s else None" % (n, n))
            elif kind == "enum":
                args.append("_e(%s, %s)" % (detail, n))
            else:
                args.append(n)
        call = "function(%s)" % ", ".join(args)
        if ret_kind == "void":
            out.append("        %s" % call)
        elif ret_kind == "cstr":
            out.append("        text = ctypes.create_string_buffer(_s(%s) or b\"\")" % call)
            out.append("        call.text = text")
            out.append("        return ctypes.cast(text, ctypes.c_void_p).value")
        else:
            out.append("        return %s or 0" % call)
        out.append("    return call")


def emit_function(out, api, f, lib):
    """The Python function over the C function f, which it reaches as `lib` (an expression)."""
    kinds = [classify(p.ctype, api) for p in f.params]
    ret_kind, ret_detail = classify(f.ret, api)

    signature = []
    before = []
    args = []
    outs = []
    skip_next_count = False
    for index, (p, (kind, detail)) in enumerate(zip(f.params, kinds)):
        n = py_name(p.name)
        last = index == len(f.params) - 1
        if skip_next_count:
            # the number of texts of the list before it
            skip_next_count = False
            args.append("len(%s_)" % py_name(f.params[index - 1].name))
            continue
        if kind == "out":
            inner = SCALAR[detail[1]] if detail[0] == "scalar" else "ctypes.c_int"
            before.append("%s_ = %s()" % (n, inner))
            args.append("ctypes.byref(%s_)" % n)
            outs.append("_e(%s, %s_.value)" % (detail[1], n) if detail[0] == "enum" else "%s_.value" % n)
            continue
        if kind == "cstr_out":
            before.append("%s_ = ctypes.c_char_p()" % n)
            args.append("ctypes.byref(%s_)" % n)
            outs.append("_r(%s_.value)" % n)
            continue
        if kind == "cstr_array":
            before.append("%s_ = [_s(item_) for item_ in (%s or [])]" % (n, n))
            before.append("%s_array = (ctypes.c_char_p * len(%s_))(*%s_)" % (n, n, n))
            args.append("%s_array" % n)
            signature.append(n)
            if not last and classify(f.params[index + 1].ctype, api) == ("scalar", "int") and "count" in f.params[index + 1].name:
                skip_next_count = True
            continue
        if kind in ("cstr", "cptr", "cbytes") and not last and f.params[index + 1].ctype == "size_t" \
                and f.params[index + 1].name in ("size", "data_size"):
            # the size of a text or of bytes is known: it is not a parameter here
            before.append("%s = _s(%s)" % (n, n) if kind == "cstr" else "%s = bytes(%s) if %s is not None else None" % (n, n, n))
            args.append(n)
            signature.append(n)
            continue
        if kind == "scalar" and p.ctype == "size_t" and p.name in ("size", "data_size") and index > 0 \
                and classify(f.params[index - 1].ctype, api)[0] in ("cstr", "cptr", "cbytes"):
            previous = py_name(f.params[index - 1].name)
            args.append("len(%s) if %s is not None else 0" % (previous, previous))
            continue
        if kind == "cstr":
            args.append("_s(%s)" % n)
        elif kind == "callback":
            args.append("_callback(%s, _adapt_%s, %s)" % (detail, detail, n))
        elif kind in ("struct_ptr", "cstruct_ptr"):
            args.append("_p(%s)" % n)
        elif kind == "enum":
            args.append("int(%s)" % n)
        elif kind == "carray":
            args.append("_a(%s, %s)" % (SCALAR[detail], n))
        else:
            args.append(n)
        signature.append("%s=None" % n if (kind == "ptr" and p.name == "user_data" and last) else n)

    out.append("def %s(%s):" % (f.name, ", ".join(signature)))
    lines = doc_lines(f.doc)
    if lines:
        out.append('    """%s"""' % "\n    ".join(lines).replace("\\", "\\\\").replace('"""', "'''"))
    for line in before:
        out.append("    %s" % line)
    call = "%s.%s(%s)" % (lib, f.name, ", ".join(args))
    if ret_kind == "void":
        out.append("    %s" % call)
        if len(outs) == 1:
            out.append("    return %s" % outs[0])
        elif outs:
            out.append("    return (%s)" % ", ".join(outs))
        return
    if ret_kind == "cstr":
        result = "_r(%s)" % call
    elif ret_kind == "enum":
        result = "_e(%s, %s)" % (ret_detail, call)
    elif ret_kind == "owned_str":
        # copied out, then given back to the library
        size = [o for o in outs if o.endswith("size_.value")]
        out.append("    address = %s" % call)
        out.append("    if not address:")
        out.append("        return None")
        out.append("    data = ctypes.string_at(address%s)" % (", " + size[0] if size else ""))
        out.append("    _lib.Rmemory_free(address)")
        out.append("    return data")
        return
    else:
        result = call
    if outs:
        out.append("    result = %s" % result)
        out.append("    return (result, %s)" % ", ".join(outs))
    else:
        out.append("    return %s" % result)



def generate(api):
    out = [HEADER % {"version": api.version}]
    emit_defines(out, api)
    out.append("")
    emit_enums(out, api)
    emit_structs(out, api)
    emit_callbacks(out, api)
    for f in api.functions:
        out.append("")
        out.append("")
        out.append("_lib.%s.argtypes = [%s]" % (f.name, ", ".join(ctype_of(api, p.ctype) for p in f.params)))
        out.append("_lib.%s.restype = %s" % (f.name, ctype_of(api, f.ret)))
        out.append("")
        out.append("")
        emit_function(out, api, f, "_lib")
    emit_mcp(out, api.mcp)
    out.append("")
    return {"python/raven.py": "\n".join(out)}


MCP_HEADER = '''

# ---- Raven MCP (raven_mcp.h, version %(version)s)
#
# Raven MCP has its own library (libraven_mcp.dylib, libraven_mcp.so,
# raven_mcp.dll), loaded by the first call of an Rmcp_ function: from the
# RAVEN_MCP_LIBRARY environment variable, then beside the Raven library, then
# beside this file, then where the system looks. Without it, the rest of this
# module works, and the Rmcp_ functions raise OSError.
#
# A callback stored in a structure (RMcpConfig.log, .on_session) is a ctypes
# object, RMcpLogCallback(function) for example, that the caller keeps alive.
'''

MCP_LOADER = '''

def _load_mcp():
    names = {"win32": ["raven_mcp.dll"], "darwin": ["libraven_mcp.dylib"]}.get(sys.platform, ["libraven_mcp.so"])
    tried = []
    explicit = os.environ.get("RAVEN_MCP_LIBRARY")
    candidates = [explicit] if explicit else []
    for folder in (os.path.dirname(_lib._name or ""), os.path.dirname(os.path.abspath(__file__))):
        if folder:
            candidates += [os.path.join(folder, name) for name in names]
    candidates += names
    for candidate in candidates:
        try:
            return ctypes.CDLL(candidate)
        except OSError as error:
            tried.append("%%s (%%s)" %% (candidate, error))
    raise OSError("Raven MCP isn't available here: its library was not found; set RAVEN_MCP_LIBRARY to its path. "
                  "Tried:\\n  " + "\\n  ".join(tried))


_mcp_lib = None


def _mcp():
    """The Raven MCP library, loaded on first use."""
    global _mcp_lib
    if _mcp_lib is None:
        lib = _load_mcp()
%(signatures)s
        _mcp_lib = lib
    return _mcp_lib
'''


def emit_mcp(out, api):
    """Raven MCP: the same shapes as Raven's API, over a library loaded on first use."""
    out.append(MCP_HEADER % {"version": api.version})
    emit_defines(out, api)
    out.append("")
    emit_enums(out, api)
    # (callbacks first: RMcpConfig holds two)
    emit_callbacks(out, api)
    emit_structs(out, api)
    signatures = []
    for f in api.functions:
        signatures.append("        lib.%s.argtypes = [%s]" % (f.name, ", ".join(ctype_of(api, p.ctype) for p in f.params)))
        signatures.append("        lib.%s.restype = %s" % (f.name, ctype_of(api, f.ret)))
    out.append(MCP_LOADER % {"signatures": "\n".join(signatures)})
    for f in api.functions:
        out.append("")
        out.append("")
        emit_function(out, api, f, "_mcp()")
