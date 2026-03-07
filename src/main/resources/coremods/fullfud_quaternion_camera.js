function initializeCoreMod() {
    var ASMAPI = Java.type('net.minecraftforge.coremod.api.ASMAPI');
    var Opcodes = Java.type('org.objectweb.asm.Opcodes');
    var InsnList = Java.type('org.objectweb.asm.tree.InsnList');
    var VarInsnNode = Java.type('org.objectweb.asm.tree.VarInsnNode');
    var MethodInsnNode = Java.type('org.objectweb.asm.tree.MethodInsnNode');
    var JumpInsnNode = Java.type('org.objectweb.asm.tree.JumpInsnNode');
    var LabelNode = Java.type('org.objectweb.asm.tree.LabelNode');
    var FieldInsnNode = Java.type('org.objectweb.asm.tree.FieldInsnNode');

    function isVarInsn(insn, opcode, index) {
        return insn != null && insn.getOpcode() === opcode && insn.var === index;
    }

    function nextMeaningful(insn) {
        var current = insn;
        while (current != null && current.getOpcode() === -1) {
            current = current.getNext();
        }
        return current;
    }

    return {
        'fullfud_quaternion_camera': {
            'target': {
                'type': 'METHOD',
                'class': 'net.minecraft.client.renderer.GameRenderer',
                'methodName': 'renderLevel',
                'methodDesc': '(FJLcom/mojang/blaze3d/vertex/PoseStack;)V'
            },
            'transformer': function(method) {
                var setAngles = ASMAPI.findFirstMethodCall(
                    method,
                    ASMAPI.MethodType.VIRTUAL,
                    'net/minecraft/client/Camera',
                    'setAnglesInternal',
                    '(FF)V'
                );

                if (setAngles == null) {
                    throw 'Failed to find Camera.setAnglesInternal in GameRenderer.renderLevel';
                }

                var startDefault = null;
                for (var insn = setAngles.getNext(); insn != null; insn = insn.getNext()) {
                    if (!isVarInsn(insn, Opcodes.ALOAD, 4)) {
                        continue;
                    }
                    var next = nextMeaningful(insn.getNext());
                    if (next instanceof FieldInsnNode
                        && next.owner === 'com/mojang/math/Axis'
                        && next.name === 'ZP') {
                        startDefault = insn;
                        break;
                    }
                }

                if (startDefault == null) {
                    throw 'Failed to find default camera rotation block start in GameRenderer.renderLevel';
                }

                var endDefault = null;
                var mulPoseCount = 0;
                for (var cursor = startDefault; cursor != null; cursor = cursor.getNext()) {
                    if (cursor instanceof MethodInsnNode
                        && cursor.owner === 'com/mojang/blaze3d/vertex/PoseStack'
                        && cursor.name === 'mulPose'
                        && cursor.desc === '(Lorg/joml/Quaternionf;)V') {
                        mulPoseCount++;
                        if (mulPoseCount === 3) {
                            endDefault = cursor.getNext();
                            break;
                        }
                    }
                }

                if (endDefault == null) {
                    throw 'Failed to find default camera rotation block end in GameRenderer.renderLevel';
                }

                var skipDefault = new LabelNode();
                var continueDefault = new LabelNode();
                var injected = new InsnList();
                injected.add(new VarInsnNode(Opcodes.ALOAD, 4));
                injected.add(new VarInsnNode(Opcodes.ALOAD, 6));
                injected.add(new VarInsnNode(Opcodes.FLOAD, 1));
                injected.add(new MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    'com/fullfud/fullfud/client/QuaternionCameraHooks',
                    'applyDroneCamera',
                    '(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/Camera;F)Z',
                    false
                ));
                injected.add(new JumpInsnNode(Opcodes.IFEQ, continueDefault));
                injected.add(new JumpInsnNode(Opcodes.GOTO, skipDefault));
                injected.add(continueDefault);
                method.instructions.insertBefore(startDefault, injected);
                method.instructions.insertBefore(endDefault, skipDefault);
                return method;
            }
        }
    };
}
