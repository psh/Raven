A `go test`, calling the driver through the package:

```go
func TestAddsANote(t *testing.T) {
	runtime.LockOSThread() // Raven belongs to the thread that starts it
	if !raven.InitHeadless() {
		t.Fatal("Raven didn't start")
	}
	defer raven.Shutdown()
	raven.SetAnimationTimeStep(1.0 / 60)
	raven.ErrorSetCallback(func(code raven.RError, severity raven.RSeverity, detail string) {
		if severity != raven.R_SEVERITY_WEAK_WARNING {
			t.Errorf("Raven: %s", detail)
		}
	})
	openNotes("notes.xml") // the app's code: builds the window, binds its actions

	find := func(selector string) uint64 {
		raven.WaitIdle(600)
		found := raven.AccessibleFind(0, selector)
		if found == 0 {
			t.Fatalf("%s: %s", selector, raven.AccessibleGetLastError())
		}
		return found
	}
	acted := func(done int) {
		if done == 0 {
			t.Fatal(raven.AccessibleGetLastError())
		}
		raven.WaitIdle(600)
	}
	acted(raven.AccessibleType(find("role=text-field"), "buy milk"))
	acted(raven.AccessibleClick(find(`role=button name="Add"`)))
	if raven.AccessibleCount(0, `role=list-item text="buy milk"`) != 1 {
		t.Error(`no note "buy milk"`)
	}
}
```

`CGO_CFLAGS="-I<raven>/include" CGO_LDFLAGS="-L/path/to/lib" go test ./...`
