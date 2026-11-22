"""UI helpers for the disposable emulator's signed-update check."""
import re


def bounds(node):
    value = node.get('bounds', '')
    if not re.fullmatch(r'\[\d+,\d+\]\[\d+,\d+\]', value):
        return None
    left, top, right, bottom = map(int, re.findall(r'\d+', value))
    return (left, top, right, bottom) if right > left and bottom > top else None


def launcher_anr_close(tree):
    """Recover only the emulator's Pixel Launcher dialog, never the app under test."""
    launcher_dialog = any(
        n.get('package') == 'android' and n.get('resource-id') == 'android:id/alertTitle'
        and n.get('text') == "Pixel Launcher isn't responding"
        for n in tree.iter('node')
    )
    if not launcher_dialog:
        return None
    for node in tree.iter('node'):
        if (node.get('package') == 'android' and node.get('resource-id') == 'android:id/aerr_close'
                and node.get('enabled') == 'true' and node.get('clickable') == 'true'):
            return bounds(node)
    return None
