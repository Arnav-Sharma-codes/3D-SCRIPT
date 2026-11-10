"""
Dataset loader for 3D Script Studio SQLite training corpus.
"""

import json
import sqlite3
import struct
from typing import List, Tuple, Dict, Any, Optional

import torch
from torch.utils.data import Dataset


class ScriptDataset(Dataset):
    """
    Loads screenplay scene sequences and supervision labels from SQLite.

    Each sample corresponds to one screenplay:
      - features: Tensor of shape [N_scenes, 32]
      - reg_targets: Tensor of shape [N_scenes, 3] (plot, char, emotion in [0, 1])
      - pacing_targets: Tensor of shape [N_scenes] (class indices 0..2)
      - structure_targets: Tensor of shape [N_scenes] (class indices 0..4)
    """

    def __init__(self, db_path: str, max_seq_len: int = 300):
        self.db_path = db_path
        self.max_seq_len = max_seq_len
        self.samples: List[Dict[str, Any]] = []
        self._load_from_db()

    def _load_from_db(self):
        try:
            conn = sqlite3.connect(self.db_path)
            cursor = conn.cursor()

            # Ensure tables exist
            cursor.execute("SELECT name FROM sqlite_master WHERE type='table' AND name='scripts'")
            if not cursor.fetchone():
                conn.close()
                return

            cursor.execute("SELECT id, title FROM scripts ORDER BY id ASC")
            scripts = cursor.fetchall()

            for script_id, title in scripts:
                cursor.execute(
                    "SELECT scene_index, features, labels FROM scene_features "
                    "WHERE script_id=? ORDER BY scene_index ASC",
                    (script_id,),
                )
                rows = cursor.fetchall()
                if not rows:
                    continue

                feat_list = []
                reg_list = []
                pacing_list = []
                struct_list = []

                for scene_idx, feat_blob, label_json in rows[: self.max_seq_len]:
                    # Unpack 32 floats (32 * 4 = 128 bytes)
                    if isinstance(feat_blob, (bytes, bytearray)) and len(feat_blob) == 128:
                        feats = list(struct.unpack("<32f", feat_blob))
                    else:
                        feats = [0.0] * 32

                    lbl = {}
                    if isinstance(label_json, str):
                        try:
                            lbl = json.loads(label_json)
                        except Exception:
                            pass

                    # Regression targets normalized to [0, 1]
                    p_score = float(lbl.get("plotScore", 5.0)) / 10.0
                    c_score = float(lbl.get("characterScore", 5.0)) / 10.0
                    e_score = float(lbl.get("emotionScore", 5.0)) / 10.0

                    pacing_id = int(lbl.get("pacingClass", 1))
                    struct_id = int(lbl.get("structuralRole", 0))

                    feat_list.append(feats)
                    reg_list.append([p_score, c_score, e_score])
                    pacing_list.append(pacing_id)
                    struct_list.append(struct_id)

                if feat_list:
                    self.samples.append({
                        "script_id": script_id,
                        "title": title,
                        "features": torch.tensor(feat_list, dtype=torch.float32),
                        "reg_targets": torch.tensor(reg_list, dtype=torch.float32),
                        "pacing_targets": torch.tensor(pacing_list, dtype=torch.long),
                        "structure_targets": torch.tensor(struct_list, dtype=torch.long),
                    })

            conn.close()
        except Exception as e:
            # If DB is unreadable or empty during design stage, log and continue
            print(f"Warning: ScriptDataset load notice ({e})")

    def __len__(self) -> int:
        return len(self.samples)

    def __getitem__(self, idx: int) -> Dict[str, Any]:
        return self.samples[idx]


def collate_scripts(batch: List[Dict[str, Any]]) -> Dict[str, torch.Tensor]:
    """
    Collate variable-length scene sequences into padded tensors.
    """
    max_len = max(item["features"].size(0) for item in batch)
    batch_size = len(batch)
    feat_dim = batch[0]["features"].size(1)

    padded_features = torch.zeros(batch_size, max_len, feat_dim, dtype=torch.float32)
    padded_reg = torch.zeros(batch_size, max_len, 3, dtype=torch.float32)
    padded_pacing = torch.full((batch_size, max_len), -100, dtype=torch.long)
    padded_structure = torch.full((batch_size, max_len), -100, dtype=torch.long)
    padding_mask = torch.ones(batch_size, max_len, dtype=torch.bool)  # True = padded

    for i, item in enumerate(batch):
        seq_len = item["features"].size(0)
        padded_features[i, :seq_len] = item["features"]
        padded_reg[i, :seq_len] = item["reg_targets"]
        padded_pacing[i, :seq_len] = item["pacing_targets"]
        padded_structure[i, :seq_len] = item["structure_targets"]
        padding_mask[i, :seq_len] = False

    return {
        "features": padded_features,
        "reg_targets": padded_reg,
        "pacing_targets": padded_pacing,
        "structure_targets": padded_structure,
        "padding_mask": padding_mask,
    }
