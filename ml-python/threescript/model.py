"""
NarrativeTransformer — PyTorch model for screenplay scene scoring.

Architecture:
  Input  : [batch, N_scenes, 32]  (32 feature dims per scene)
  Encoder: 3-layer Transformer (4 heads, hidden=128, ffn=256)
  Heads  :
    regression     → [batch, N_scenes, 3]  (plot, character, emotion in [0,10])
    pacing_logits  → [batch, N_scenes, 3]  (Dead / Steady / Dynamic)
    structure_logits → [batch, N_scenes, 5] (5 structural roles)
"""

import torch
import torch.nn as nn
import math


class PositionalEncoding(nn.Module):
    """Standard sinusoidal positional encoding for scene sequence position."""

    def __init__(self, d_model: int, max_len: int = 300, dropout: float = 0.1):
        super().__init__()
        self.dropout = nn.Dropout(p=dropout)

        pe = torch.zeros(max_len, d_model)
        position = torch.arange(0, max_len, dtype=torch.float).unsqueeze(1)
        div_term = torch.exp(
            torch.arange(0, d_model, 2).float() * (-math.log(10000.0) / d_model)
        )
        pe[:, 0::2] = torch.sin(position * div_term)
        pe[:, 1::2] = torch.cos(position * div_term)
        pe = pe.unsqueeze(0)  # [1, max_len, d_model]
        self.register_buffer("pe", pe)

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        x = x + self.pe[:, : x.size(1)]
        return self.dropout(x)


class NarrativeTransformer(nn.Module):
    """
    Transformer encoder that learns narrative structure from scene feature sequences.

    Parameters
    ----------
    input_dim   : int   Feature dimension per scene (default 32)
    hidden_dim  : int   Internal model dimension (default 128)
    num_heads   : int   Attention heads (default 4)
    num_layers  : int   Transformer encoder layers (default 3)
    ffn_dim     : int   Feed-forward sublayer width (default 256)
    dropout     : float Dropout probability (default 0.1)
    max_seq_len : int   Maximum scene count per script (default 300)
    """

    def __init__(
        self,
        input_dim: int = 32,
        hidden_dim: int = 128,
        num_heads: int = 4,
        num_layers: int = 3,
        ffn_dim: int = 256,
        dropout: float = 0.1,
        max_seq_len: int = 300,
    ):
        super().__init__()

        self.input_proj = nn.Linear(input_dim, hidden_dim)
        self.pos_enc = PositionalEncoding(hidden_dim, max_len=max_seq_len, dropout=dropout)

        encoder_layer = nn.TransformerEncoderLayer(
            d_model=hidden_dim,
            nhead=num_heads,
            dim_feedforward=ffn_dim,
            dropout=dropout,
            batch_first=True,
            norm_first=True,  # Pre-norm for training stability
        )
        self.transformer = nn.TransformerEncoder(encoder_layer, num_layers=num_layers)
        self.norm = nn.LayerNorm(hidden_dim)

        # Task heads
        self.regression_head = nn.Sequential(
            nn.Linear(hidden_dim, 64),
            nn.GELU(),
            nn.Dropout(dropout),
            nn.Linear(64, 3),   # plot, character, emotion
            nn.Sigmoid(),       # → [0, 1], scaled to [0, 10] at inference
        )

        self.pacing_head = nn.Sequential(
            nn.Linear(hidden_dim, 32),
            nn.GELU(),
            nn.Linear(32, 3),   # Dead / Steady / Dynamic
        )

        self.structure_head = nn.Sequential(
            nn.Linear(hidden_dim, 32),
            nn.GELU(),
            nn.Linear(32, 5),   # 5 structural roles
        )

    def forward(self, x: torch.Tensor, padding_mask: torch.Tensor = None):
        """
        x             : [batch, N_scenes, input_dim]
        padding_mask  : [batch, N_scenes] bool mask (True = ignore)

        Returns dict with:
          regression        : [batch, N_scenes, 3]  — values in [0, 10]
          pacing_logits     : [batch, N_scenes, 3]
          structure_logits  : [batch, N_scenes, 5]
        """
        h = self.input_proj(x)             # [B, N, hidden]
        h = self.pos_enc(h)                # + positional encoding
        h = self.transformer(h, src_key_padding_mask=padding_mask)
        h = self.norm(h)

        regression = self.regression_head(h) * 10.0   # [0,10]
        pacing_logits = self.pacing_head(h)
        structure_logits = self.structure_head(h)

        return {
            "regression": regression,
            "pacing_logits": pacing_logits,
            "structure_logits": structure_logits,
        }
