import { useEffect, useState } from "react";

export function useResource(load, enabled = true) {
    const [revision, setRevision] = useState(0);
    const [result, setResult] = useState({ key: null, revision: -1, data: null, error: "", ready: false });
    useEffect(() => {
        if (!enabled) return;
        const controller = new AbortController();
        let active = true;
        load(controller.signal).then((data) => {
            if (active) setResult({ key: load, revision, data, error: "", ready: true });
        }).catch((error) => {
            if (active) setResult({ key: load, revision, data: null, error: error.message, ready: true });
        });
        return () => { active = false; controller.abort(); };
    }, [load, enabled, revision]);
    const current = enabled && result.key === load && result.revision === revision
        ? result : { data: null, error: "", ready: false };
    return { data: current.data, error: current.error, loading: enabled && !current.ready,
        refresh: () => setRevision(value => value + 1) };
}
