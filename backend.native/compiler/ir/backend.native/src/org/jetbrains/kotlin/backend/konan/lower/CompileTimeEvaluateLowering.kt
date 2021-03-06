package org.jetbrains.kotlin.backend.konan.lower

import org.jetbrains.kotlin.backend.common.FileLoweringPass
import org.jetbrains.kotlin.backend.common.lower.IrBuildingTransformer
import org.jetbrains.kotlin.backend.common.lower.at
import org.jetbrains.kotlin.backend.konan.Context
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.types.isString
import org.jetbrains.kotlin.ir.util.irCall
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe

internal class CompileTimeEvaluateLowering(val context: Context): FileLoweringPass {

    override fun lower(irFile: IrFile) {
        irFile.transformChildrenVoid(object: IrBuildingTransformer(context) {
            override fun visitCall(expression: IrCall): IrExpression {
                expression.transformChildrenVoid(this)

                val descriptor = expression.descriptor.original
                // TODO
                if (descriptor.fqNameSafe.asString() != "kotlin.collections.listOf" || descriptor.valueParameters.size != 1)
                    return expression
                val elementsArr = expression.getValueArgument(0) as? IrVararg
                    ?: return expression

                // The function is kotlin.collections.listOf<T>(vararg args: T).
                // TODO: refer functions more reliably.

                if (elementsArr.elements.any { it is IrSpreadElement }
                        || !elementsArr.elements.all { it is IrConst<*> && it.type.isString() })
                    return expression


                builder.at(expression)

                val typeArgument = expression.getTypeArgument(0)!!
                return builder.irCall(context.ir.symbols.listOfInternal.owner, listOf(typeArgument)).apply {
                    putValueArgument(0, elementsArr)
                }
            }
        })
    }
}