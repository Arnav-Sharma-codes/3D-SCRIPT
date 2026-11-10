"""
Export NarrativeTransformer model to ONNX format.
"""

import os
from typing import Optional
import torch
import torch.nn as nn
from .model import NarrativeTransformer


class _ExportWrapper(nn.Module):
    """Wrapper that unwraps dict output to tuple for clean ONNX export."""

    def __init__(self, model: NarrativeTransformer):
        super().__init__()
        self.model = model

    def forward(self, x: torch.Tensor):
        out = self.model(x)
        return out["regression"], out["pacing_logits"], out["structure_logits"]


def export_to_onnx(
    model: NarrativeTransformer,
    output_path: str,
    input_dim: int = 32,
    opset_version: int = 14,
) -> str:
    """
    Exports a trained NarrativeTransformer model to ONNX.

    Args:
        model: Trained NarrativeTransformer instance
        output_path: Target path for .onnx file
        input_dim: Scene feature dimension (default 32)
        opset_version: ONNX operator set version

    Returns:
        Absolute path to the exported ONNX model
    """
    os.makedirs(os.path.dirname(os.path.abspath(output_path)), exist_ok=True)
    model.eval()

    wrapper = _ExportWrapper(model)
    dummy_input = torch.randn(1, 10, input_dim, dtype=torch.float32)

    torch.onnx.export(
        wrapper,
        dummy_input,
        output_path,
        export_params=True,
        opset_version=opset_version,
        do_constant_folding=True,
        input_names=["x"],
        output_names=["regression", "pacing_logits", "structure_logits"],
        dynamic_axes={
            "x": {0: "batch_size", 1: "num_scenes"},
            "regression": {0: "batch_size", 1: "num_scenes"},
            "pacing_logits": {0: "batch_size", 1: "num_scenes"},
            "structure_logits": {0: "batch_size", 1: "num_scenes"},
        },
    )

    return os.path.abspath(output_path)
